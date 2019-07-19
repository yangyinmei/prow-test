/*
 * 全局变量定义
 */
def tag = env.TAG
def mailList = env.MAILTO

//状态
def buildStatus = "unset"
//异常模块名称
def errModuleName

def mailSubjectStr

def tasks = [:]

def allList = [
	"admin-billing-ui",
	"admin-crm-ui",
	"admin-oss-ui",
	"admin-product-ui",
	"admin-public-ui",
	"account-center-service",
	"account-service",
	"agent-web",
	"api-gateway-service",
	"api-payment-service",
	"area-service",
//	"billing",
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
	"message-receive-worker",
	"note-center-service",
	"note-service",
//	"operator-service",
	"operator-ui",
	"order-center-service",
//	"order-job",
	"order-service",
	"partner-service",
	"partner-ui",
	"platform-cache-config",
	"platform-config",
	"pms-center-service",
	"pms-frontend-conax-service",
//	"pms-frontend-ott-service",
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


def billingList = [
	"billing",
	"order-job",
	"jobserver"
	]

def resRepository = '10.0.251.196'
def desRepository = '10.0.251.190'
def checkSyncRepository = 'http://10.0.251.190'
def checkSyncUser = "admin"
def checkSyncPASSWORD = "asdf1234"
def checkSyncprojectName = 'boss,billing'
def checkSyncdesRepositoryStr = 'harborrelease'

allList.each{moduleName->
	moduleName = moduleName.toLowerCase()
	tasks[moduleName] = {
		node('jnlp'){
			
			
			//从196获取镜像
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('get image from 196'){
						if(moduleName in billingList){
							if(moduleName.equals("jobserver")) {
								sh """
									docker pull ${resRepository}"/billing/spark-jobserver:"${tag}
									docker pull ${resRepository}"/billing/spark-jobserver-yarn:"${tag}
									docker pull ${resRepository}"/billing/uploadjarfile:"${tag}
								"""
							}else {
								sh """
									docker pull ${resRepository}"/billing/"${moduleName}":"${tag}
								"""
							}
						}else {
							sh """
								docker pull ${resRepository}"/boss/"${moduleName}":"${tag}
							"""
						}
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = moduleName
				}
			}
			
			
			//登录190仓库
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('login 190'){
						sh """
							docker login -u admin  -p asdf1234 10.0.251.190
						"""
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = moduleName
				}
			}
			
			
			//重命名镜像
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('rename image'){
						if(moduleName in billingList){
							if(moduleName.equals("jobserver")) {
								sh """
									docker tag ${resRepository}"/billing/spark-jobserver:"${tag} ${desRepository}"/billing/spark-jobserver:"${tag}
									docker tag ${resRepository}"/billing/spark-jobserver-yarn:"${tag} ${desRepository}"/billing/spark-jobserver-yarn:"${tag}
									docker tag ${resRepository}"/billing/uploadjarfile:"${tag} ${desRepository}"/billing/uploadjarfile:"${tag}
								"""
							}else {
								sh """
									docker tag ${resRepository}"/billing/"${moduleName}":"${tag} ${desRepository}"/billing/"${moduleName}":"${tag}
								"""
							}
						}else {
							sh """
								docker tag ${resRepository}"/boss/"${moduleName}":"${tag} ${desRepository}"/boss/"${moduleName}":"${tag}
							"""
						}
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = moduleName
				}
			}
			
			
			//推送镜像到190
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('push image'){
						if(moduleName in billingList){
							if(moduleName.equals("jobserver")) {
								sh """
									docker push ${desRepository}"/billing/spark-jobserver:"${tag}
									docker push ${desRepository}"/billing/spark-jobserver-yarn:"${tag}
									docker push ${desRepository}"/billing/uploadjarfile:"${tag}
								"""
							}else {
								sh """
									docker push ${desRepository}"/billing/"${moduleName}":"${tag}
								"""
							}
						}else {
							sh """
								docker push ${desRepository}"/boss/"${moduleName}":"${tag}
							"""
						}
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = moduleName
				}
			}
			
			
			//删除镜像
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('delete image'){
						if(moduleName in billingList){
							if(moduleName.equals("jobserver")) {
								sh """
									docker rmi ${desRepository}"/billing/spark-jobserver:"${tag}
									docker rmi ${desRepository}"/billing/spark-jobserver-yarn:"${tag}
									docker rmi ${desRepository}"/billing/uploadjarfile:"${tag}
							
									docker rmi ${resRepository}"/billing/spark-jobserver:"${tag}
									docker rmi ${resRepository}"/billing/spark-jobserver-yarn:"${tag}
									docker rmi ${resRepository}"/billing/uploadjarfile:"${tag}
								"""
							}else {
								sh """
									docker rmi ${desRepository}"/billing/"${moduleName}":"${tag}
									docker rmi ${resRepository}"/billing/"${moduleName}":"${tag}
								"""
							}
						}else {
							sh """
								docker rmi ${desRepository}"/boss/"${moduleName}":"${tag}
								docker rmi ${resRepository}"/boss/"${moduleName}":"${tag}
							"""
						}
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = moduleName
				}
			}
			
		}
	}
}


parallel tasks


//发送邮件
node('jnlp') {
	
	/**
	* 检查镜像同步状态
	*/
	if(buildStatus.contentEquals("unset")){
		try{
			stage('check image sync'){
				build job: 'check-harbor-sync', parameters: [
						string(name: 'url', value: checkSyncRepository),
						string(name: 'USERNAME', value: checkSyncUser),
						string(name: 'PASSWORD', value: checkSyncPASSWORD),
						string(name: 'projectName', value: checkSyncprojectName),
						string(name: 'TAG', value: tag),
						string(name: 'desRepositoryStr', value: checkSyncdesRepositoryStr)]
			}
		}catch(err){
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "check image sync"
		}
	}
	
	
	/*
	 * 发送邮件
	 */
	stage('send mail'){
		
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
								<li>Failed stage &nbsp;：${errModuleName}</li>
                                <li>本次推送镜像标签&nbsp;：${tag}</li>
			                </ul>
			            </td>
			        </tr>
			    </table>
			</body>
			</html>
		"""
		
		if(buildStatus.contentEquals("unset")) {
			mailSubjectStr = "Success! Images of tag:${tag} have been pushed to ${desRepository}"
		}else {
			mailSubjectStr = "Failed! transferImages for ${tag} failed!"
		}
		
		
		emailext body: "${bodyStr}",
		recipientProviders: [
			[$class: 'DevelopersRecipientProvider'],
			[$class: 'RequesterRecipientProvider']
			],
		subject: "${mailSubjectStr}",
		mimeType: 'text/html',
		//to: 'bizh@startimes.com.cn'
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












