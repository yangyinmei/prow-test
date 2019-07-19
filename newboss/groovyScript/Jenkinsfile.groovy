import java.text.SimpleDateFormat


/*
 * 全局变量定义
 */
//传入的构建过程变量
def branch = env.BRANCH
def oldTag = env.OLDTAG
def newTag = env.NEWTAG
def version = env.VERSION
def jobName = env.JOB_NAME
def buildnum = env.BUILD_NUMBER
def buildType = env.BUILDTYPE
def ifTransferImages = env.TRANSFER
def yamlBranch = 'master'
def yamlTemplateBranch = env.VERSION

//有BUG，WORKSPACE变量在pipline中用不了
//def innerWorkspace = env.WORKSPACE


//环境相关
//节点中的工作目录，与jenkins系统配置中的pod模板中的working directory一致
def workDirectory = "/home/jenkins"

//基础镜像当中的第三方公共jar包目录
def thirjarsDirInBaseImage = "/public-boss-jarfiles"

//构建服务器中的第三方公共jar包目录（遍历，获得列表）
def thirjarsDirInBuildImage = "/home/public-boss-jarfiles"

//定义新标签
def nowdateFormat = new SimpleDateFormat("yyMMdd")
def nowDateStr = nowdateFormat.format(new Date())
//如果传入的新标签为空，则此处生成新标签，如果不为空，则使用传入的新标签
//指定标签时使用
def tag
if(newTag) {
	tag = "${newTag}"
}else {
	tag = "${version}-${buildnum}-${nowDateStr}"
}

//接收增量构建变更模块的列表
def currentModuleList = []

//需要构建的模块列表
def buildModuleList

//并行任务集合
def tasks = [:]

//全量列表
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


def jiraTaskUrl

def buildTime

def buildStatus = "unset"

//增量构建的变更模块
def changedModules

//异常模块名称
def errModuleName

//邮件标题
def mailSubjectStr


/*
 * 如果是增量构建获取代码,打标签，并执行:deploy:tools:version-review:run任务，获得变更模块
 * 如果是全量构建，则只获取代码，打标签
 */
def sourceCode = "sourceCode"
node('jnlp'){
	
	
	/*
	 * 创建存放构建镜像的日志目录，原本放在各模块构建镜像的python脚本中，由于并发，会引起创建目录出错
	 * 所以放在此处，单进程创建。
	 */
	try {
		stage('create dir for imagelog'){
			dir('/home/ci-python'){
				sh """
					mkdir -p logfile/${tag}"_buildimages_log"
				"""
			}
		}
	}catch(err) {
		buildStatus = "FALSE"
		echo "${err}"
		errModuleName = "create dir for imagelog"
	}
	
	
	/*
	 * 创建ftp中存放构建产物的父目录，如果放导各节点当中创建，会产生并发问题（各模块的目录仍然在各节点中上传时创建）
	 */
	if(buildStatus.contentEquals("unset")) {
		try {
			stage('create ftp dir'){
				sh """
					chmod 777 /home/ci-python/createFtpDir.py
					python /home/ci-python/createFtpDir.py ${version} ${tag}
				"""
			}
		}catch(err){
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
				def dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm")
				def getCodeDate = new Date()
				buildTime = dateFormat.format(getCodeDate)
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
							git clone -b ${yamlBranch} http://jenkins:startimes@10.0.250.70:8088/rd-platform/yaml.git
						else
							cd yaml
							git checkout ${yamlBranch}
							git pull origin ${yamlBranch}
						fi
						# 创建本次构建存放yaml文件的目录
						mkdir -p /home/yamldir/yaml/boss_yaml/${version}/${tag}/changed_module
						mkdir -p /home/yamldir/yaml/boss_yaml/${version}/${tag}/nochanging_module
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
						if [ `git tag --list ${tag}` ]
						then
							echo "${tag} exists."
						else
							echo "${tag} not exist."
							git config --global user.name "jenkins"
							git config --global user.email "bizh@startimes.com.cn"
							git config --local remote.origin.url http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git
							git tag -a ${tag} -m "BUILDER: "
							git push origin ${tag}
						fi
					"""
				}
			}
		}catch(err) {
			buildStatus = "FALSE"
			echo "${err}"
			errModuleName = "tag code"
		}
	}
	
	
	
	/*
	 * 如果是增量构建，还需执行:deploy:tools:version-review:run任务，获得变更模块
	 */
	if(buildType.contentEquals('incremental')) {
		
		//如果增量构建未输入旧标签，则获取当前分支对应的上次构建生成的标签作为旧标签
		if(!oldTag) {
			
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('oldTag is null'){
						oldTag = readFile("/home/transfer/${branch}LASTTAG.txt").replace(" ", "").replace("\n", "")
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = "oldTag is null"
				}
			}
			
		}
		
		
		//执行获取新旧标签差别任务
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get moduleDependence.txt'){
					dir(sourceCode){
						sh """
							chmod 777 gradlew
							export JAVA_HOME=/usr/java/jdk1.8.0_66/
							export version1=${oldTag}
							export version2=${tag}
							export working_directory=${workDirectory}/workspace/${jobName}/${sourceCode}
							./gradlew clean :deploy:tools:version-review:run
						"""
					}
				}
			}catch(err){
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get moduleDependence.txt"
			}
		}
		
		
		//获得变更模块列表
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('get changedModuleList'){
					dir(sourceCode){
						moduleStr = readFile('moduleDependence.txt').replace(" ", "")
						println moduleStr
						println oldTag
						println tag
						moduleList = moduleStr.tokenize(',')
						
						//排除当前未发布的模块（不在allList中的模块）
						//currentModuleList = moduleList.clone()
						
						moduleList.each{module->
							def existFlag = (module  in allList)
							if(existFlag) {
								currentModuleList << module
							}
						}
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "get changedModuleList"
			}
		}
		
		
	}
	
}


//增量构建或全量构建
if(buildType.contentEquals('incremental')) {
	buildModuleList = currentModuleList.clone()
}else {
	buildModuleList = allList.clone()
}


// 如果列表为空，则不需要构建
if(!buildModuleList) {
	echo "There has been no changes in any module since last successful building."
	changedModules = "No changings between ${oldTag} and ${tag}."
}else {
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
									git add boss_yaml
									git commit -m ${tag}
									git push origin ${yamlBranch}
									git tag -a ${tag} -m "${tag}"
									git push origin ${tag}
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
			try {
				if((ifTransferImages.toLowerCase()).contentEquals('TRUE'.toLowerCase())) {
					stage('trigger transferImages'){
						build job: 'transferImages', parameters: [string(name: 'TAG', value: tag)], wait: false
					}
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "trigger transferImages"
			}
		}
		
		
		
		/*
		 * 将增量构建的两次标签之间的提交内容，记录到jira中
		 */
		if(buildType.contentEquals('incremental')) {
			
			//将增量构建两次标签之间的修改提交信息记录到jira
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('record changings to jira'){
						sh """
							git clone -b ${tag} http://jenkins:startimes@10.0.250.70:8088/boss/stariboss.git
							cd stariboss
							commitMessage=`git log --oneline --grep STARIBOSS ${oldTag}..${tag} | awk '{$1="";print $0}'`
							chmod 777 /home/ci-python/recordChanggingsToJira.py
							python /home/ci-python/recordChanggingsToJira.py ${oldTag} ${tag} "\$commitMessage" ${branch}
						"""
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = "record changings to jira"
				}
			}
			
			
			//获得新提交的issue链接
			if(buildStatus.contentEquals("unset")) {
				try {
					stage('get issue url'){
						jiraTaskUrl = readFile("/home/transfer/issueUrlFile").replace(" ", "").replace("\n", "")
					}
				}catch(err) {
					buildStatus = "FALSE"
					echo "${err}"
					errModuleName = "get issue url"
				}
			}
			
		}
		
		
		/*
		 * 将新标签作为旧标签记录，如果下次增量构建未提供旧标签，则使用该记录
		 */
		if(buildStatus.contentEquals("unset")) {
			try {
				stage('record tag as oldTag'){
					sh """
								echo ${tag} > /home/transfer/${branch}LASTTAG.txt
							"""
				}
			}catch(err) {
				buildStatus = "FALSE"
				echo "${err}"
				errModuleName = "record tag as oldTag"
			}
		}
		
		
		/*
		 * ==============
		 * 发送邮件
		 * ==============
		 */
		stage('send mail'){
			
			// body
			changedModules = currentModuleList.join("<br>")

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
											<li>构建类型&nbsp;：&nbsp;${buildType}</li>
			                                <li>旧标签&nbsp;：${oldTag}</li>
			                                <li>新标签&nbsp;：${tag}</li>
											<li>incremental build changings&nbsp;：<a href="${jiraTaskUrl}">${jiraTaskUrl}</a></li>
											<li>构建时间&nbsp;：${buildTime}</li>
			                                <li>代码分支&nbsp;：${branch}</li>
								            <li>git项目地址ַ&nbsp;：<a href="http://10.0.250.70:8088/rd-platform/yaml.git">http://10.0.250.70:8088/rd-platform/yaml.git</a></li>
											<li>git上的YAML目录&nbsp;：&nbsp;.../boss_yaml/${version}/${tag}/</li>
						                    <li>变更模块&nbsp;：&nbsp;<br>${changedModules}</li>
						                </ul>
						            </td>
						        </tr>
						    </table>
						</body>
						</html>
					"""
			
			
			// subject
			
//					if(hudson.model.Result.SUCCESS.equals(currentBuild.rawBuild.getPreviousBuild()?.getResult())) {
//						buildStatus = "Success"
//					}else {
//						buildStatus = "Failed"
//					}
			
			if(buildStatus.contentEquals("unset")) {
				mailSubjectStr = "Success! buildtype: ${buildType} tag:${tag}"
			}else {
				mailSubjectStr = "${buildStatus}! buildtype: ${buildType} tag:${tag}"
			}
			
			emailext body: "${bodyStr}",
			recipientProviders: [
				[$class: 'DevelopersRecipientProvider'],
				[$class: 'RequesterRecipientProvider']
				],
			subject: "${mailSubjectStr}",
			mimeType: 'text/html',
			to: 'yinzy@startimes.com.cn,bizh@startimes.com.cn,jink@startimes.com.cn,sunt@startimes.com.cn'
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


