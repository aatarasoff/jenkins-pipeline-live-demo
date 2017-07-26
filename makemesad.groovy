    def call(body) {

        def config = [:]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()


        rtGradle.run switches: gradleVersion + ' --info', tasks: 'sonarqube'

    }
