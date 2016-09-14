node() {
    stage 'Building haproxy-agent JAR'
    docker.image('maven:3.3.3-jdk-8').inside {
        scm checkout
        def version = version()
        def commit = commitSha1()
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