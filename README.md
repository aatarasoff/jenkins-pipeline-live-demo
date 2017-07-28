# jenkins-pipeline-live-demo

## Demo for rewriting classic Jenkins pipeline to declarative
Steps to launch:

1. Run `docker-compose up -d` to up Jenkins, Sonar and Artifactory
2. Go to `localhost:8080` (this is Jenkins). Use **user/user** for login.
3. Create pipeline job and put contents of `pipeline.groovy` into the internal editor. Then launch it.
4. Feel free to rewrite it in a declarative way and compare with our solution that you could find out in `dp.groovy`
5. Or feel free to cheat and looking at `dp.groovy` immediately
