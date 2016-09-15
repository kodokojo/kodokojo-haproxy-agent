node() {
    stage 'Building haproxy-agent running JAR'
    docker.image('maven:3.3.3-jdk-8').inside {
        checkout scm
        def version = version()
        def commit = commitSha1()
        slackSend channel: '#dev', color: 'good', message: "Build job ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) SUCCESS\n${report}\nA new version of Kodo Kojo is availalbe on (<https://${env_name}.kodokojo.io/|https://${env_name}.kodokojo.io/>) ", teamDomain: 'kodo-kojo', token: 'TipNoOZm8yjFgSNTc2hpRYdI'
        sh 'mvn -B install'
        if (currentBuild.result != 'FAILURE') {
            slackSend channel: '#dev', color: 'good', message: "Building version $version from branch *${GIT_BRANCH}* on commit ${GIT_COMMIT} \n Job ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) *SUCCESS*\n${report}"
        } else {
            step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/-*.xml'])
            step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
            slackSend channel: '#dev', color: 'danger', message: "Building version $version from branch *${GIT_BRANCH}* on commit ${GIT_COMMIT} \n Job ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) *FAILED*\n${report}"
        }
    }
}

def version() {
    def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
    matcher ? matcher[0][1] : null
}

def commitSha1() {
    sh 'git rev-parse HEAD > commit'
    def commit = readFile('commit').trim()
    sh 'rm commit'
    commit
}
