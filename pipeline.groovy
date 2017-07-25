def version = "${env.BUILD_NUMBER}"

node('docker') {
    stage 'Checkout'
    git changelog: false, poll: false, url: 'https://github.com/aatarasoff/spring-boot-example-for-jenkins-pipeline'
    
    stage 'Build jar'
    def server = Artifactory.server 'artifactory'
    def rtGradle = Artifactory.newGradleBuild()
    rtGradle.useWrapper = true
    rtGradle.deployer server: server, repo: 'maven'
    
    def gradleVersion = '-Pversion=' + version
    
    rtGradle.run switches: gradleVersion, tasks: 'build'

    stage 'QA'
    withSonarQubeEnv('sonar') {
        rtGradle.run switches: gradleVersion + ' --info', tasks: 'sonarqube'
    }

    sleep 10

    def qg = waitForQualityGate()
    if (qg.status != 'OK') {
       error "Pipeline aborted due to quality gate failure: ${qg.status}"
    }

    stage 'Publish jar'
    def buildInfo = rtGradle.run switches: gradleVersion, tasks: 'build artifactoryPublish'

    stage 'Publish docker'
    sh "curl -o app.jar http://artifactory:8081/artifactory/maven/demo/${version}/demo-${version}.jar"
    docker.withServer('tcp://socatdockersock:2375') {
       docker.withRegistry('http://docker.artifactory:8000') {
            docker.build("demo").push("${version}")
       }
    }
}

node('docker') {
  stage 'Deploy'
  docker.withServer('tcp://socatdockersock:2375') {
    sh """docker run --net jenkinspipelinelivedemo_default \
    --name demo${version} -d -p 10080 docker.artifactory:8000/demo:${version}"""
  }

  stage 'Post-deploy check'
  def checkCommand = createCheckCommand(version)
  waitUntil {
    try {
      sh "${checkCommand}"
      true
    } catch(error) {
      sleep 10
      currentBuild.result = 'SUCCESS'
      false
    }
  }

  stage 'Finalize'
  docker.withServer('tcp://socatdockersock:2375') {
    sh "docker rm -f demo${version}"
    sh "docker rmi -f docker.artifactory:8000/demo:${version}"
  }
}

def createCheckCommand(version) {
  return "curl http://demo${version}:10080/health"
}
