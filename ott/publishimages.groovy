def jobName = env.JOBNAME
def tag = env.TAG
def pythonDir = env.PYTHONDIR
def mailList = env.MAILTO
def buildStatus = "unset"
def errModuleName
def srcURL = "http://10.0.251.196"
def srcUserName = "admin"
def srcPassword = "asdf1234"
def desURL = "http://10.0.251.190"
def desUserName = "admin"
def desPassword = "asdf1234"

def imageProjectDic = [
	"ottapplication" : "ottapplication",
	"ottplatform" : "starott-platform",
	"staros" : "starott",
	"starcdn-gslb" : "starcdn-gslb",
	"staret" : "starott-et",
]
def projectName = imageProjectDic[jobName]

node('jnlp') {
	try {
        stage('prepare environment') {
            checkout scm
            op = load ("./common/operation.groovy")
        }
    } catch(err) {
        buildStatus = "FALSE"
        echo "${err}"
        errModuleName = "prepare environment"
    }

	try {
		stage('publish images') {
			retry (3) {
				sh """
					chmod 777 ${pythonDir}/publishImages.py
					python ${pythonDir}/publishImages.py ${srcURL} ${desURL} ${srcUserName} ${srcPassword} ${desUserName} ${desPassword} ${projectName} ${tag}
				"""
			}
		}
	} catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "publish images"
	}
	/*
	 * 发送邮件
	 */
	stage('send mail') {
		def bodyContent = """
            <li>Failed stage &nbsp;：${errModuleName}</li>
            <li>本次推送镜像标签&nbsp;：${tag}</li>
        """
        if(buildStatus.contentEquals("unset")) {
            mailSubjectStr = "Success! Images of tag:${tag} have been pushed to ${desURL}"
        }else {
            mailSubjectStr = "Failed! publishImages for ${tag} failed!"
        }
        op.sendMail(jobName, buildStatus, mailList, bodyContent, mailSubjectStr)
	}
	
	//构建失败
	if(!(buildStatus.contentEquals("unset"))) {
		stage('build faild'){
			echo "Error stage: ${errModuleName}"
			error 'build faild'
		}
	}
}