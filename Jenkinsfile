node() {
    stage 'Building haproxy-agent running JAR'
    docker.image('maven:3.3.3-jdk-8').inside {
        checkout scm
        def version = version()
        def commit = commitSha1()
        sh "echo Build $version from commit $commit"
        sh 'mvn -B install'
    }
}

def version() {
    def matcher = readFile('pom.xml') =~ '<version>(.+)-.*</version>'
    matcher ? matcher[0][1].tokenize(".") : null
}

def commitSha1() {
    sh 'git rev-parse HEAD > commit'
    def commit = readFile('commit').trim()
    sh 'rm commit'
    commit
}
