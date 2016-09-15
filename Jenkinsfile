node() {
    stage 'Building haproxy-agent running JAR'
    docker.image('maven:3.3.3-jdk-8').inside {
        checkout scm
        def version = version()
        def commit = commitSha1()
        slackSend channel: '#dev', color: 'good', message: "Build job ${env.JOB_NAME} ${env.BUILD_NUMBER} from branch *${env.GIT_BRANCH}* starting (<${env.BUILD_URL}|Open>)"
        sh 'mvn -B install'
        if (currentBuild.result != 'FAILURE') {
            slackSend channel: '#dev', color: 'good', message: "Building version $version from branch *${env.GIT_BRANCH}* on commit ${commit} \n Job ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) *SUCCESS*"
        } else {
            step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/-*.xml'])
            step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
            slackSend channel: '#dev', color: 'danger', message: "Building version $version from branch *${env.GIT_BRANCH}* on commit ${commit} \n Job ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) *FAILED*"
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
