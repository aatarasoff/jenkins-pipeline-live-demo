def version = "${env.BUILD_NUMBER}"
def server = Artifactory.server 'artifactory'
def rtGradle = Artifactory.newGradleBuild()

pipeline {
  environment {
      gradleVersion = "--offline -Pversion=${env.BUILD_NUMBER}"
  }

  agent {
    label 'docker'
  }

  stages {
    stage('Checkout') {
      steps {
        git changelog: false, poll: false, url: 'https://github.com/aatarasoff/spring-boot-example-for-jenkins-pipeline'
      }
    }

    stage('Build jar') {
      steps {
        script {
          rtGradle.useWrapper = true
          rtGradle.deployer server: server, repo: 'maven'

          rtGradle.run switches: env.gradleVersion, tasks: 'build'
        }
      }
    }

    stage('QA') {
      when {
        expression {
          withSonarQubeEnv('sonar') {
              sh './gradlew ' + env.gradleVersion + ' --info sonarqube'
          }
    
          sleep 5

          return waitForQualityGate().status != 'OK'
        }
      }
      steps {
        error "Pipeline aborted due to quality gate failure"
      }
    }

    stage('Publish jar') {
      steps {
        script {
          def buildInfo = rtGradle.run switches: env.gradleVersion, tasks: 'build artifactoryPublish'
        }
      }
    }

    stage('Publish Docker') {
      steps {
        sh "curl -o app.jar http://artifactory:8081/artifactory/maven/demo/${version}/demo-${version}.jar"

        script {
          docker.withServer('tcp://socatdockersock:2375') {
             docker.withRegistry('http://docker.artifactory:8000') {
                  docker.build("demo").push("${version}")
             }
          }
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          docker.withServer('tcp://socatdockersock:2375') {
            sh """docker run --net jenkinspipelinelivedemo_default \
            --name demo${version} -d -p 10080 docker.artifactory:8000/demo:${version}"""
          }
        }
      }
    }

    stage('Post-deploy') {
      steps {
        timeout(20) {
          retry(3) {
            sleep 5
            sh "curl http://demo${version}:10080/health"
          }
        }
      }
    }
  }

  post {
    always {
      script {
        docker.withServer('tcp://socatdockersock:2375') {
          sh "docker rm -f demo${version}"
          sh "docker rmi -f docker.artifactory:8000/demo:${version}"
        }
      }
    }
  }
}
