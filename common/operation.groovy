import java.text.SimpleDateFormat
import java.sql.*
import groovy.sql.Sql

def generateTag(nowDateStr, moduleStr, newTag, branch, version, buildnum, transferDir, group) {
    def tag

    //单独构建模块
    if(moduleStr) {
        if(branch.contentEquals("hotfix_release") && group.contentEquals("hotfix")) {
            // 将记录文件的所有tag号生产一个列表：lastTagList
            def lastTagStr = readFile("${transferDir}/${branch}${group}LASTTAG.txt").replace(" ", "").replace("\n", ",")
            def lastTagList = lastTagStr.tokenize(',')
            println lastTagList
            // 将当前记录文档做临时备份，生产空的记录文档，在遍历的时候，生成新的记录文档；如果构建失败，则恢复备份文档为记录文档
        	sh """
                rm -rf ${transferDir}/${branch}${group}LASTTAGBak.txt
                mv ${transferDir}/${branch}${group}LASTTAG.txt ${transferDir}/${branch}${group}LASTTAGBak.txt
                touch ${transferDir}/${branch}${group}LASTTAG.txt
            """
            // 获得最新小版本号，小版本号根据符号"."划分，长度为4
        	def lastTagVersion
            for(lastTag in lastTagList) {
                if(lastTag.toString().split("\\.").length == 4) {
                    lastTagVersion = lastTag.substring(0, lastTag.lastIndexOf("."))
                    println "lastTagVersion:"+lastTagVersion
                    sh """
                        echo ${lastTag} >> ${transferDir}/${branch}${group}LASTTAG.txt
                    """
                    break
                }
            }
            // 如果最新小版本号打过补丁，则当前补丁号+1
            lastTagList.each { lastTag->
                if(lastTag.toString().split("\\.").length != 4) {
                    lastTagPrefix = lastTag.substring(0, lastTag.lastIndexOf(".")-7)
                    println "lastTagPrefix:"+lastTagPrefix
                    if(lastTagPrefix.contentEquals(lastTagVersion)) {
                    	def lastNumber = (lastTag.substring(lastTag.lastIndexOf(".") + 1)).toInteger()
                        def patchNumber = (lastNumber + 1).toString()
                        tag = lastTagPrefix + "." + nowDateStr + "." + patchNumber
                        sh """
		                    echo ${tag} >> ${transferDir}/${branch}${group}LASTTAG.txt
		                """
                    } else {
                    	sh """
	                        echo ${lastTag} >> ${transferDir}/${branch}${group}LASTTAG.txt
	                    """
                    }
                }
            }
            // 如果最新小版本号没有打过补丁，则补丁号初始化为1
            if(!tag) {
            	tag = lastTagVersion + "." + nowDateStr + ".1"
            	sh """
                    echo ${tag} >> ${transferDir}/${branch}${group}LASTTAG.txt
                """
            }
        } else {
            //确定非hotfix分支的补丁标签: version-buildnumber-YYMMDD
            tag = "${version}-${buildnum}-${nowDateStr}-patch"
        }
    } else {
        if(newTag) {
            tag = "${newTag}"
        } else {
            if(branch.contentEquals("hotfix_release") && group.contentEquals("hotfix")) {
                def lastTagStr = readFile("${transferDir}/${branch}${group}LASTTAG.txt").replace(" ", "").replace("\n", ",")
                def lastTagList = lastTagStr.tokenize(',')
                println lastTagList
                sh """
                    rm -rf ${transferDir}/${branch}${group}LASTTAGBak.txt
                    mv ${transferDir}/${branch}${group}LASTTAG.txt ${transferDir}/${branch}${group}LASTTAGBak.txt
                    touch ${transferDir}/${branch}${group}LASTTAG.txt
                """
                // 获得当前最新小版本号及其前缀
                lastTagList.each { lastTag->
                    if(lastTag.toString().split("\\.").length == 4) {
                        def versionPrefix = version.substring(0, version.lastIndexOf("."))
                        def lastTagPrefix = lastTag.substring(0, lastTag.lastIndexOf("."))
                        def secondLastTagPrefix = lastTagPrefix.substring(0, lastTagPrefix.lastIndexOf("."))
                        println "lastTag:"+lastTag
                        println "lastTagPrefix:"+lastTagPrefix
                        println "secondLastTagPrefix" + secondLastTagPrefix
                        println "versionPrefix" + versionPrefix

                        //如果上次版本号前缀与本次版本号前缀相同，则本次标签为上次标签+1，如果不相同，则本次标签为本次输入的版本号
                        if(secondLastTagPrefix.contentEquals(versionPrefix)) {
                            def lastNumber = (lastTagPrefix.substring(lastTagPrefix.lastIndexOf(".") + 1)).toInteger()
                            def newNumberStr = (lastNumber + 1).toString()
                            tag = secondLastTagPrefix + "." + newNumberStr + "." + nowDateStr
                        } else {
                            tag = version + "." + nowDateStr
                        }
                        //如果本次输入的版本号小于上次构建的版本号，tag=-1
                        String[] tagArray = tag.substring(0, version.lastIndexOf(".")).toString().split("\\.");
                        String[] lastTagArray = lastTag.substring(0, version.lastIndexOf(".")).toString().split("\\.");
                        def len = tagArray.length > lastTagArray.length ? lastTagArray.length:tagArray.length
                        for(i = 0; i < len; i++) {
                            if(tagArray[i].length() > lastTagArray[i].length()) {
                                break
                            }else if(tagArray[i].length() < lastTagArray[i].length()) {
                                tag = "-1"
                                break
                            }else if(tagArray[i].length() == lastTagArray[i].length()) {
                                if(tagArray[i] > lastTagArray[i]) {
                                    break
                                }
                                if(tagArray[i] < lastTagArray[i]) {
                                    tag = "-1"
                                    break
                                }
                            }
                        }
                        if(tag.contentEquals("-1")) {
                            sh """
                                echo ${lastTag} >> ${transferDir}/${branch}${group}LASTTAG.txt
                            """
                        } else {
                            sh """
		                        echo ${tag} >> ${transferDir}/${branch}${group}LASTTAG.txt
		                    """
                        }
                    } else {
                    	sh """
                            echo ${lastTag} >> ${transferDir}/${branch}${group}LASTTAG.txt
                        """
                    }
                }
            } else {
                //非hotfix分支
                tag = "${version}-${buildnum}-${nowDateStr}"
            }
        }
    }
    println "newTag:"+tag
    return tag
}

def channelClosedErr(errMessageList) {
    errMessageList.each { err->
        if(err.contains("The channel is closing down or has closed down")) {
            return true
        }
    }
    return false
}

def deleteLocalTag(sourceCodeDir, tag) {
    dir(sourceCodeDir) {
        sh """
            if [ `git tag --list ${tag}` ]
            then
                git tag -d ${tag}
                echo "delete local tag: ${tag}"
            fi
        """
    }
}

def createFtpDir(version, tag, pythonDir, uploadFtpDir) {
    sh """ 
        chmod 777 ${pythonDir}/createFtpDir.py
        python ${pythonDir}/createFtpDir.py ${version} ${tag} ${uploadFtpDir}
    """
}

def getCode(sourceCodeDir, moduleName, branch) {
    sh """
        hostname
        rm -rf ${moduleName}
        mkdir ${moduleName}
        cp -rf ${sourceCodeDir}/. ${moduleName}/
    """
}

def build(codeDir, moduleName, pythonDir, jobName) {
    if(!moduleName.contentEquals("account-billing")) {
        sh """
            export BOSS_JAVA_HOME_11=/usr/java/jdk-11.0.1
        """
    }
    sh """
        env | grep LANG
        export EXTERNAL_JAR_DIR=/public-boss-jarfiles
        export patchJarDir=/patch/
        chmod 777 ${pythonDir}/build.py
        python ${pythonDir}/build.py ${codeDir} ${moduleName} ${jobName}
        cd ./${moduleName}
        . /etc/profile
        . ./gradleTaskList.config
        chmod 777 gradlew
        ./gradlew clean \$GRADLETASK 2> /tmp/log.txt
    """
}

def moveBuildingOutputToDir(codeDir, moduleName, pythonDir) {
    sh """
        rm -rf ${codeDir}/outputFileFolder/
        chmod 777 ${pythonDir}/mvFileToOutputFileFolder.py
        python ${pythonDir}/mvFileToOutputFileFolder.py ${codeDir} ${moduleName}
    """
    if(moduleName.contentEquals("account-billing")) {
        sh"""
            mkdir -p ${codeDir}/outputFileFolder/account-billing
            cp ${codeDir}/account-billing/build/libs/* ${codeDir}/outputFileFolder/account-billing/
        """
    }
}

def refactorOutput(codeDir, moduleName, pythonDir) {
    if(moduleName.contentEquals("platform-activiti")) {
        sh"""
            cd ${codeDir}/outputFileFolder/
            unzip activiti-rest.rar
        """
    } else {
        if(moduleName.contentEquals("account-billing")) {
            sh"""
                cd ${codeDir}/outputFileFolder/account-billing
                mv stariboss-account-billing.jar BillingMain.jar
            """
        }
        sh """
            chmod 777 ${pythonDir}/refactorOutput.py
            python ${pythonDir}/refactorOutput.py ${codeDir} ${moduleName}
        """
    }    
}

def modifyThirdJarsAndBuildImages(codeDir, moduleName, tag, pythonDir) {
    retry (3) {
        sh """
            chmod 777 ${pythonDir}/buildDockerImage.py
            python ${pythonDir}/buildDockerImage.py ${codeDir} ${moduleName} ${tag}
        """
    }
}

def deleteImage(moduleName, tag, jobName) {
    def imageName = moduleName.toLowerCase()
    sh """
        docker rmi 10.0.251.196/${jobName}/${imageName}:${tag}
    """
}

def uploadBuildingProductToFtp(codeDir, moduleName, version, tag, pythonDir, uploadFtpDir, jobName, ziplist) {
    // 将解压后移除公共jar包的文件夹重新打包成war包
    if(jobName.contentEquals("stariboss-helm")) {
        if(moduleName in ziplist) {
            sh """
                cd ${codeDir}/outputFileFolder
                echo ${tag} > ./${moduleName}/lib/version.txt
                zip -r ${moduleName}-${tag}.zip ./${moduleName}/*
                rm -rf ${moduleName}.zip
                rm -rf ${moduleName}
            """
        } else if(moduleName.contentEquals("platform-activiti")) {
            sh"""
                cd ${codeDir}/outputFileFolder/activiti-rest
                echo ${tag} > ./WEB-INF/classes/version.txt
                jar cvf ../activiti-rest-${tag}.war ./
                rm -rf ${codeDir}/outputFileFolder/activiti-rest
            """
        } else if(moduleName.contentEquals("account-billing")) {
            sh"""
                cd ${codeDir}/outputFileFolder/account-billing
                echo ${tag} > ./version.txt
                tar -cvf ../account-billing-${tag}.tar ./*
                rm -rf ${codeDir}/outputFileFolder/account-billing
            """
        } else {
            sh """
                cd ${codeDir}/outputFileFolder/
                cp ./context.xml ./${moduleName}/META-INF/
                cd ${moduleName}
                echo ${tag} > ./WEB-INF/classes/version.txt
                jar cvf ../${moduleName}-${tag}.war ./
                rm -rf ../${moduleName}.war
                rm -rf ../${moduleName}
            """
        }
    }
    retry (3) {
        sh """
            chmod 777 ${pythonDir}/uploadProductToFtp.py
            python ${pythonDir}/uploadProductToFtp.py ${codeDir} ${moduleName} ${version} ${tag} ${uploadFtpDir} ${jobName}
        """
    }
}

def deleteProductFromFtp(version, tag, uploadFtpDir, pythonDir) {
    sh """
        chmod 777 ${pythonDir}/deleteFtpProduct.py
        python ${pythonDir}/deleteFtpProduct.py ${version} ${tag} ${uploadFtpDir}
    """
}

def recordBuildModuleListIntoTxt(buildModuleList, allList_boss, pms_frontend_moduleList, buildType, dir, version, tag) {
    buildModuleList.each { moduleName->
        if(moduleName.contentEquals("pms-frontend-conax-service")) {
            pms_frontend_moduleList.each { pmsModuleName->
                sh """ echo ${pmsModuleName} >> ${dir}/${version}/${tag}/change.txt """
            }
        } else {
            sh """ echo ${moduleName} >> ${dir}/${version}/${tag}/change.txt """
        }
    }
}

def createAndUploadYamlFiles(version, tag, moduleName, pythonDir, yamlDir, yamlTemplateDir, yaml_kind_list) {
    if(moduleName.contentEquals("account-billing")) {
        sh """
            mkdir -p ${yamlDir}/${version}/${tag}/changed_module/${moduleName}/job
        """
    } else {
        yaml_kind_list.each { kind->
            sh """
                mkdir -p ${yamlDir}/${version}/${tag}/changed_module/${moduleName}/${kind}
            """
        }
    }

    sh """
        chmod 777 ${pythonDir}/createDpmAndSvcYaml.py
        python ${pythonDir}/createDpmAndSvcYaml.py ${moduleName} ${tag} ${version} changed_module ${yamlTemplateDir} ${yamlDir}
    """
}

def createAndUploadYamlFilesForNochanged(version, tag, moduleName, pythonDir, yamlDir, yamlTemplateDir, yaml_kind_list) {
    if(moduleName.contentEquals("account-billing")) {
        //创建本次构建存放yaml文件的目录
        sh """
            mkdir -p ${yamlDir}/${version}/${tag}/nochanging_module/${moduleName}/job
        """
    } else {
        yaml_kind_list.each { kind->
            sh """
                mkdir -p ${yamlDir}/${version}/${tag}/nochanging_module/${moduleName}/${kind}
            """
        }
    }
    sh """
        chmod 777 ${pythonDir}/createDpmAndSvcYaml.py
        python ${pythonDir}/createDpmAndSvcYaml.py ${moduleName} ${tag} ${version} nochanging_module ${yamlTemplateDir} ${yamlDir}
    """
}

def tagCode(tag, sourceCodeDir) {
    dir(sourceCodeDir) {
        sh """
            git push origin ${tag}
        """
    }
}

def recordCurrentVersionAndTag(version, tag, dir) {
    sh """ echo '${version}/${tag}' > ${dir}/version.txt """
}

def pushYamlToGit(version, tag, yamlBranch, yamlDir) {
    retry (3) {
        dir(yamlDir) {
            sh """
                echo 'nameserver 192.168.5.225' > /etc/resolv.conf
                git config --global user.name 'jenkins'
                git config --global user.email 'jenkins@startimes.com.cn'
                git checkout ${yamlBranch}
                git pull origin ${yamlBranch}
                git add .
                git diff-index --quiet HEAD || git commit -a -m ${tag}
                git push origin ${yamlBranch}
            """
        }
    }
}

def recordTagAsOldTag(tag, branch, transferDir, group) {
    sh """ echo ${tag} > ${transferDir}/${branch}${group}LASTTAG.txt """
    // release分支构建时,需要记录版本号到hotfix_release内,供hotfix_release增量构建时使用--孙涛需求
    // if(branch.contentEquals("release")) {
    //     sh """ echo ${tag} > ${transferDir}/hotfix_release${group}LASTTAG.txt """
    // }
}

def getInfoForCompileFailed(errMessageList) {
    def bugInfoList = []
    def bugLogMessage
    def errJavaFileDir
    def errJavaFile
    def developers = ""
    errMessageList.each { err->
        def failIndex = err.indexOf("FAILURE: Build failed with an exception.")
        println "failIndex: " + failIndex
        if(failIndex != -1) {
            bugLogMessage = err[err.lastIndexOf("/home/")..-1].replace("<br>", "")
            println "bugLogMessage: " + bugLogMessage
            err = err[0..failIndex-1]
            def errJavaIndex = 0
            def flag = 0
            while(flag != -1) {
                errJavaIndex = err.indexOf(".java", errJavaIndex)
                errJavaIndex = errJavaIndex + ".java".length()
                flag = err.indexOf(".java", errJavaIndex)
            }
            println "errJavaIndex: " + errJavaIndex
            def tmpStr = err[0..errJavaIndex-1]
            errJavaFileDir = tmpStr[tmpStr.lastIndexOf("/home/")..tmpStr.lastIndexOf("/")]
            errJavaFile = tmpStr[tmpStr.lastIndexOf("/")+1..-1]
            println "errJavaFileDir: " + errJavaFileDir
            println "errJavaFile: " + errJavaFile
            dir(errJavaFileDir) {
                GIT_COMMIT_NAME = sh (
                    script: "git log --format='%an' -- ${errJavaFile}",
                    returnStdout: true
                ).trim().tokenize("\n").unique()
            }
            // 获取编译失败的开发人员
            GIT_COMMIT_NAME.each { name->
                if(!name.toLowerCase().contentEquals("changyj") && !name.toLowerCase().contentEquals("jenkins.startimes.com.cn")) {
                    if(developers.contentEquals("")) {
                        developers = "${name}"
                    }
                }
            }
        }
    }
    if(developers.contentEquals("杨银梅")) {
        developers = "yangym"
    }
    echo "developers: ${developers}" 
    bugInfoList << bugLogMessage
    bugInfoList << errJavaFile
    bugInfoList << developers
    return bugInfoList
}

def sendMail(jobName, buildStatus, mailList, bodyContent, mailSubjectStr) {
    def bodyStr = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <title>\${jobName"}</title>
        </head>
        <body leftmargin="8" marginwidth="0" topmargin="8" marginheight="4"
            offset="0">
            <table width="95%" cellpadding="0" cellspacing="0"
                style="font-size: 11pt; font-family: Tahoma, Arial, Helvetica, sans-serif">
                <tr>
                    <td>(本邮件是程序自动下发的，请勿回复！)</td>
                </tr>
                <tr>
                    <td>
                        <ul>
                            ${bodyContent}
                        </ul>
                    </td>
                </tr>
            </table>
        </body>
        </html>
    """

    emailext body: "${bodyStr}",
    recipientProviders: [
        [$class: 'DevelopersRecipientProvider'],
        [$class: 'RequesterRecipientProvider']
        ],
    subject: "${mailSubjectStr}",
    mimeType: 'text/html',
    to: "${mailList},yangym@startimes.com.cn"
}

return this;