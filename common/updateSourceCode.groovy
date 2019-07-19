def gitUser = "jenkins"
def gitPassword = "startimes"
def buildStatus = "unset"
def jobName = env.JOBNAME
def project =  env.PROJECT
def branch = env.BRANCH
def gitUrl = env.GITURL

node('master') {
    stage('config git info') {
        sh"""
            echo "###Config git info###"
            git config --global user.name 'jenkins'
            git config --global user.email 'jenkins@startimes.com.cn'
            git config --global http.postBuffer 524288000
        """
    }

    stage('update code') {
        try {
            retry (3) {
                sh"""
                    echo "###start! checkout code###"
                    cd /home/nfs/
                    if [ ! -d ${jobName} ];
                    then
                        mkdir ${jobName}
                    fi
                    cd ${jobName}
                    if [ ! -d ${project} ];
                    then
                        mkdir ${project}
                        git init
                        git clone http://${gitUser}:${gitPassword}@${gitUrl} ${project}
                    fi
                    cd /home/nfs/${jobName}/${project} || exit 1
                    git fetch || exit 1
                    git checkout ${branch} || exit 1
                    git reset --hard origin/${branch} || exit 1
                    git pull http://${gitUser}:${gitPassword}@${gitUrl} ${branch}:${branch} || exit 1
                    echo "###over! checkout code###"
                """
                // 重试休眠3秒
                sleep 3
            }
        } catch(err) {
            buildStatus = "FALSE"
            echo "${err}"
        }
    }

    //更新代码失败
    if(!(buildStatus.contentEquals("unset"))) {
        stage('build faild') {
            error 'build faild'
        }
    }
}