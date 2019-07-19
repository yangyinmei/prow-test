import java.text.SimpleDateFormat


/*
 * 全局变量定义
 */
//传入的构建过程变量
def branch = env.BRANCH
def version = env.VERSION
def jobName = env.JOB_NAME
def buildnum = env.BUILD_NUMBER
def moduleStr = env.moduleStr
def mailList = env.MAILTO
def ifTransferImages = env.ifTransferImages
def yamlBranch = 'master'
def yamlTemplateBranch
def tagRecord = "${branch}${group}LASTTAG.txt"

//标签变量是否是本次构建增加到git中的
def tagIsNew

//环境相关
//节点中的工作目录，与jenkins系统配置中的pod模板中的working directory一致

def workDirectory = "/home/jenkins"
//基础镜像当中的第三方公共jar包目录
def thirjarsDirInBaseImage = "/public-boss-jarfiles"
//构建服务器中的第三方公共jar包目录（遍历，获得列表）
def thirjarsDirInBuildImage = "/home/public-boss-jarfiles"

//定义标签
def nowdateFormat = new SimpleDateFormat("yyMMdd")
def nowDateStr = nowdateFormat.format(new Date())

def buildStatus = "unset"
def errModuleName
def mailSubjectStr

//补丁标签
def patchTag

//确定补丁标签
node('jnlp'){
	try {
		stage('establish patchTag'){
			if(branch.contentEquals("hotfix")) {
				//确定hotfix的补丁标签：版本标签-buildnumber-YYMMDD
				def versionTag = readFile("/home/transfer/${tagRecord}").replace(" ", "").replace("\n", "")
				patchTag = "${versionTag}-${buildnum}-${nowDateStr}-patch"
			}else {
				//确定非hotfix分支的补丁标签: version-buildnumber-YYMMDD
				patchTag = "${version}-${buildnum}-${nowDateStr}-patch"
			}
		}
	}catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "establish patchTag"
	}
	
	
	/*
	 * 确定yaml文件模板的分支：如果有输入参数VERSION对应的模板分支，则取这个分支的模板文件，如果没有，则取master分支的模板文件
	 */
	try {
		stage('establish yaml template branch'){
			def tempYamlTemplateBranch = env.VERSION
			sh """
				git clone  http://jenkins:startimes@10.0.250.70:8088/rd-platform/yaml-template.git
				cd yaml-template
				yamltembranch=`git branch --all --list "origin/"${tempYamlTemplateBranch}`
				if [ -n "\$yamltembranch" ]
				then
					echo "${tempYamlTemplateBranch}" > ./yamlTemplateBranch.txt
				else
					echo "master" > ./yamlTemplateBranch.txt
				fi
			"""
			yamlTemplateBranch = readFile("/home/jenkins/workspace/worker-alone/yaml-template/yamlTemplateBranch.txt").replace(" ", "").replace("\n", "")
			println yamlTemplateBranch
		}
	}catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "establish yaml template branch"
	}
}

def tasks = [:]

def allList = [
	"account-center-service",
	"account-service",
	"admin-billing-ui",
	"admin-crm-ui",
	"admin-oss-ui",
	"admin-product-ui",
	"admin-public-ui",
	"agent-web",
	"api-gateway-service",
	"api-payment-service",
	"api-mboss-service",
	"area-service",
	"billing",
	"callcenter-proxy",
	"card-center-service",
	"card-service",
	"channel-service",
	"check-service",
	"collection-center-service",
	"collection-service",
	"customer-center-service",
	"customer-service",
	"customer-ui",
	"haiwai-proxy",
	"iom-center-service",
	"iom-service",
	"job-service",
	"jobserver",
	"knowledge-service",
	"knowledge-ui",
	"message-center-service",
	"note-center-service",
	"note-service",
//	"operator-service",
	"operator-ui",
	"order-center-service",
	"order-job",
	"order-service",
	"partner-service",
	"partner-ui",
	"platform-cache-config",
	"platform-config",
	"pms-center-service",
	"pms-frontend-conax-service",
	"pms-frontend-ott-service",
	"pms-partition-service",
	"portal-ui",
	"problem-center-service",
	"problem-service",
	"product-service",
	"resource-center-service",
	"resource-service",
	"resource-ui",
	"starDA-web",
	"system-service",
	"worker-ui",
	]



def sourceCode = "sourceCode"

def	moduleList = moduleStr.tokenize(',')

// 排除当前未发布的模块
def currentModuleList = moduleList.clone()

moduleList.each{module->
	def existFlag = (module  in allList)
	if(!existFlag) {
		currentModuleList = (currentModuleList - module)
	}
}


if(!currentModuleList) {
	echo "There has been no changes in any module since last successful building."
}else {
	node('jnlp'){
		
		/*
		 * 创建存放构建镜像的日志目录，原本放在各模块构建镜像的python脚本中，由于并发，会引起创建目录出错
		 * 所以放在此处，单进程创建。
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('create dir for imagelog'){
					dir('/home/ci-python'){
						sh """
							mkdir -p logfile/${patchTag}"_buildimages_log"
						"""
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "create dir for imagelog"
			}
		}
		
		
		/*
		 * 创建ftp中存放构建产物的父目录，如果放导各节点当中创建，会产生并发问题（各模块的目录仍然在各节点中上传时创建）
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('create ftp dir'){
					sh """
						chmod 777 /home/ci-python/createFtpDir.py
						python /home/ci-python/createFtpDir.py ${version} ${patchTag}
					"""
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "create ftp dir"
			}
		}
		
		
		/*
		 * 获取新的代码，作为构建依据
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get code as sourceCode'){
					//如果job的BRANCH变量，输入tag，则此处获取tag对应的代码
					sh """
						git clone -b ${branch} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git ${sourceCode}
					"""
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get code as sourceCode"
			}
		}
		
		
		/*
		 * 获得对应版本的yaml模板文件
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get yaml template'){
					dir('/home/yamldir/'){
						sh """
							if [ ! -d "/home/yamldir/yaml-template/.git" ]
							then
								rm -rf /home/yamldir/yaml-template/
								git clone -b ${yamlTemplateBranch} http://jenkins:startimes@10.0.250.70:8088/rd-platform/yaml-template.git
							else
								cd yaml-template
								git checkout ${yamlTemplateBranch}
								git config --global user.name "jenkins"
								git config --global user.email "bizh@startimes.com.cn"
								git pull origin ${yamlTemplateBranch}
							fi
						"""
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get yaml template"
			}
		}
		
		
		/*
		 * 获取或更新git上的yaml
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get yaml from git'){
					
					dir('/home/yamldir/'){
						sh """
							if [ ! -d "/home/yamldir/yaml/.git" ]
							then
								rm -rf /home/yamldir/yaml/
								git clone -b ${yamlBranch} http://bizh:startimes@10.0.250.70:8088/rd-platform/yaml.git
							else
								cd yaml
								git checkout ${yamlBranch}
								git pull origin ${yamlBranch}
							fi
							# 创建本次构建存放yaml文件的目录
							mkdir -p /home/yamldir/yaml/boss_yaml/${version}/${patchTag}/changed_module
							mkdir -p /home/yamldir/yaml/boss_yaml/${version}/${patchTag}/nochanging_module
						"""
					}
				
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get yaml from git"
			}
		}
		
	
		/*
		 * 为代码打标签
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('tag code'){
					
					dir(sourceCode){
						sh """
							if [ `git tag --list ${patchTag}` ]
							then
								echo "${patchTag} exists."
								echo "NO" > tagIsNew.CONF
							else
								echo "${patchTag} not exist."
								git config --global user.name "jenkins"
								git config --global user.email "bizh@startimes.com.cn"
								git config --local remote.origin.url http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git
								git tag -a ${patchTag} -m "BUILDER: "
								git push origin ${patchTag}
								echo "YES" > tagIsNew.CONF
							fi
						"""
						tagIsNew = readFile("tagIsNew.CONF").replace(" ", "").replace("\n", "")
					}
					
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "tag code"
			}
		}
		
	
	}
	
	
	currentModuleList.each{moduleName->
		def codeDir = "${workDirectory}/workspace/${jobName}/${moduleName}"
		tasks[moduleName] = {
			println moduleName
			
			node('jnlp'){
				
				//如果变更模块中有agent-web，单独处理agent-web
				if(moduleName.contentEquals("agent-web")) {
					if(buildStatus.contentEquals("unset")) {
						try {
							build job: 'agent-web', parameters: [string(name: 'TAG', value: patchTag), string(name: 'IMAGETAG', value: patchTag), string(name: 'VERSION', value: version)]
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = moduleName
						}
					}
				}else {
					
					
					/*
					 * ==============
					 * 获取各模块代码
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('get code'){
								sh """
								git clone -b ${patchTag} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git ${moduleName}
							"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "get code"
						}
					}
					
					
					/*
					 * ==============
					 * 节点当中构建对应模块
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('build'){
								sh """
								echo "nameserver 192.168.5.225" > /etc/resolv.conf
								chmod 777 /home/ci-python/build.py
								python /home/ci-python/build.py ${codeDir} ${moduleName}
								cd ./${moduleName}
								. /etc/profile
								. ./gradleTaskList.config
								./gradlew clean \$GRADLETASK
							"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "build"
						}
					}
					
					
					/*
					 * ==============
					 * 将构建产物和模块对应的dockerfile移动到代码根目录下的outputFileFolder文件夹中
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('move building output to dir: outputFileFolder'){
								sh """
								chmod 777 /home/ci-python/mvFileToOutputFileFolder.py
								python /home/ci-python/mvFileToOutputFileFolder.py ${codeDir} ${moduleName}
							"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "move building output to dir: outputFileFolder"
						}
					}
					
					
					/*
					 * ==============
					 * 删除公共jar包，并构建镜像，上传镜像
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('modify third jars and build images'){
								sh """
								chmod 777 /home/ci-python/buildDockerImage.py
								python /home/ci-python/buildDockerImage.py ${codeDir} ${moduleName} ${patchTag}
							"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "modify third jars and build images"
						}
					}
					
					
					/*
					 * 待稳定以后，将推送镜像到190的功能，在此处实现，通过参数确定是否推送镜像
					 * 目前，先以单独任务实现推送镜像的功能，避免推送镜像失败，导致构建也需要重新运行
					 */
					
					
					/*
					 * ==============
					 * 删除生成的镜像
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('delete image'){
								def imageName = moduleName.toLowerCase()
								if(imageName in ["billing", "order-job", "jobserver"]) {
									if(imageName == "jobserver") {
										sh """
										docker rmi 10.0.251.196/billing/spark-jobserver:${patchTag}
										docker rmi 10.0.251.196/billing/spark-jobserver-yarn:${patchTag}
										docker rmi 10.0.251.196/billing/uploadjarfile:${patchTag}
									"""
									}else {
										sh """
										docker rmi 10.0.251.196/billing/${imageName}:${patchTag}
								 	"""
									}
								}else {
									sh """
									docker rmi 10.0.251.196/boss/${imageName}:${patchTag}
							 	"""
								}
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "delete image"
						}
					}
					
					
					/*
					 * ==============
					 * 将构建产物上传ftp中
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('upload building product to ftp'){
								sh """
								chmod 777 /home/ci-python/uploadProductToFtp.py
								python /home/ci-python/uploadProductToFtp.py ${codeDir} ${moduleName} ${version} ${patchTag}
							"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "upload building product to ftp"
						}
					}
					
					
					
					/*
					 * ==============
					 * 生成yaml文件
					 * ==============
					 */
					if(buildStatus.contentEquals("unset")) {
						try {
							stage('create and upload yaml files'){
								sh """
								chmod 777 /home/ci-python/createDpmAndSvcYaml.py
								python /home/ci-python/createDpmAndSvcYaml.py ${codeDir} ${moduleName} ${patchTag} ${version} changed_module /home/yamldir/yaml-template/boss_yaml /home/yamldir/yaml/boss_yaml
							"""
							}
						}catch(err) {
							buildStatus = "FALSE"
							echo "${err}"
							errModuleName = "create and upload yaml files"
						}
					}
				}
				
			}
			
		}
	}
	parallel tasks
	
	node('jnlp') {
		
		/*
		 * 推送本次构建生成的yaml到git
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('push yaml to git'){
					dir('/home/yamldir/yaml/'){
						sh """
							git config --global user.name "jenkins"
							git config --global user.email "bizh@startimes.com.cn"
							git checkout ${yamlBranch}
							git pull origin ${yamlBranch}
							git add boss_yaml
							git commit -m ${patchTag}
							git push origin ${yamlBranch}
							git tag -a ${patchTag} -m "${patchTag}"
							git push origin ${patchTag}
						"""
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "push yaml to git"
			}
		}
		
		
		/*
		 * 参数确定是否触发推送镜像到190的任务
		 */
		if(buildStatus.contentEquals("unset")) {
			if((ifTransferImages.toLowerCase()).contentEquals('true')) {
				stage('trigger transferImages'){
					build job: 'transferPartImages', parameters: [string(name: 'TAG', value: patchTag), string(name: 'MODULES', value: moduleStr)], wait: false
				}
			}
		}
		
		
		/*
		 * 如果有需要构建的模块，但是构建失败，并且新标签为本次刚创建的标签，且标签已推送到远程，从远程git删除标签
		 */
		if(!(buildStatus.contentEquals("unset"))) {
			if((tagIsNew.contentEquals("YES"))) {
				stage('delete tag'){
					sh """
						git clone -b hotfix http://bizh:startimes@10.0.250.70:8088/boss/stariboss.git
						cd stariboss
						existTag=\$(git tag|grep ^${patchTag}\$)

						if [ -n "\$existTag" ]
						then
							git push origin --delete ${patchTag}
							echo "tag ${patchTag} has been delete on remote."
						else
							echo "tag \$existTag not exist, need not delete."
						fi
						"""
				}
			}
		}
		
		
		stage('send mail'){
			
			// body
			def changedModules = currentModuleList.join("<br>")

			def bodyStr = """
				<!DOCTYPE html>
				<html>
				<head>
				<meta charset="UTF-8">
				<title>\${ENV, var="JOB_NAME"}</title>
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
									<li>Failed stage&nbsp;：&nbsp;<br>${errModuleName}</li>
									<li>本次代码标签&nbsp;：&nbsp;<br>${patchTag}</li>
									<li>本次镜像标签&nbsp;：&nbsp;<br>${patchTag}</li>
				                    <li>构建模块&nbsp;：&nbsp;<br>${changedModules}</li>
				                </ul>
				            </td>
				        </tr>
				    </table>
				</body>
				</html>
			"""
			
			
			// subject
			if(buildStatus.contentEquals("unset")) {
				mailSubjectStr = "Success!  tag:${patchTag}"
			}else {
				mailSubjectStr = "Failed!  tag:${patchTag}"
			}
			
			emailext body: "${bodyStr}",
			recipientProviders: [
				[$class: 'DevelopersRecipientProvider'],
				[$class: 'RequesterRecipientProvider']
				],
			subject: "${mailSubjectStr}",
			mimeType: 'text/html',
			//to: 'yinzy@startimes.com.cn,bizh@startimes.com.cn,jink@startimes.com.cn,sunt@startimes.com.cn'
			to: mailList
		}
		
		
		//构建失败
		if(!(buildStatus.contentEquals("unset"))) {
			stage('build faild'){
				echo "Error stage: ${errModuleName}"
				error 'build faild'
			}
		}
	}
		

	
}








