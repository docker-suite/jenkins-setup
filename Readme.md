# ![](https://github.com/docker-suite/artwork/raw/master/logo/png/logo_32.png) jenkins-setup
[![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg?style=flat-square)](https://opensource.org/licenses/MIT)

This repository contains a full [jenkins] setup which install :

- [docker-suite `jenkins`][dsuite-jenkins] container
- [docker-suite `jenkins-docker`][dsuite-jenkins-docker] container to be used as a docker cloud


## ![](https://github.com/docker-suite/artwork/raw/master/various/pin/png/pin_16.png) Usage

```bash
$ make build
$ make up
Creating JenkinsDocker ... done
Creating JenkinsServer ... done
```


## ![](https://github.com/docker-suite/artwork/raw/master/various/pin/png/pin_16.png) Details

The [`docker-compose.yaml`](./docker-compose.yaml) file defines following components:

- Jenkins Network: Defines underlying bridge network.
- Jenkins Server: This is jenkins master.
- Jenkins Docker: A `docker:dind` container to enable running docker inside docker.


### Jenkins Network:

Here we will be setting up the underlying network for the containers to communicate between themself. We create a bridge network with name space as `jenkins`.

```yaml
networks:
  jenkins:
    driver: bridge # Defines bridge network to be used by services defined later.
```


### Jenkins Docker:

It is a good practice to run the Jenkins job inside docker containers rather than Jenkins host machine itself. 

- This enables us to maintain isolation between multiple Jenkins Pipelines and Jobs.
- Also we ahieve an easily reproducible/debuggable job execution environment setup.

Since this is a Jenkins Server setup running as docker container we would need to setup `docker-inside-docker`, i.e to be able to run docker commands and containers (JOBS containers) inside another docker container (Jenkins Server Container). This is made possible with using `docker:dind` container.

Here JenkinsDocker container starts docker-engine and exposes it at address `tcp://docker:2376`. This address will be used later by JenkinsServer container to bring up Jobs containers.

```yaml
services:
  jenkins_docker:
    image: dsuite/jenkins-docker
    networks:
      jenkins:
        aliases:
          - docker # Defines to use jenkins network defined above also under the alias name `docker`.
    container_name: JenkinsDocker
    hostname: JenkinsDocker
    privileged: true
    environment:
      - DOCKER_TLS_CERTDIR=/certs
    ports:
      - "2376:2376" # Exposes docker server port 2376 to be used by jenkins server container at "tcp://docker:2376".
    volumes:
      - ./jenkins-agent:/home/jenkins/agent # Preserves Jenkins agent workspace
      - ./jenkins-data:/home/jenkins/home # Preserves Jenkins data like job definitions, credentials, build logs, etc.
      - ./jenkins-docker-certs:/certs/client # Docker client certs.
```


### Jenkins Server:

JenkinsServer container is using a customized image [`jenkins-docker`][dsuite-jenkins-docker]and can be accessed at [http://localhost:8080](http://localhost:8080).

```yaml
services:
  jenkins_server:
    image: dsuite/jenkins
    networks:
      - jenkins # Use jenkins network defined earlier
    container_name: JenkinsServer
    hostname: JenkinsServer
    restart: always
    environment: # Define docker env variable to connect to docker-engine defined in JenkinsDocker container.
      - DOCKER_HOST=tcp://docker:2376
      - DOCKER_CERT_PATH=/certs/client
      - DOCKER_TLS_VERIFY=1
    ports:
      - "8080:8080" # For UI
      - "50000:50000" # For API
    volumes:
      - ./jenkins-data:/var/jenkins_home:rw # Preserves Jenkins data like job definitions, credentials, build logs, etc.
      - ./jenkins-docker-certs:/certs/client:ro # Docker client certs.
```


## ![](https://github.com/docker-suite/artwork/raw/master/various/pin/png/pin_16.png) Configuring Docker Cloud

Go to **Manage Nodes and Clouds** and then **Configure Clouds** and **Add a new cloud**. The type of `docker` should automatically appear in the dropdown.

Set the `Docker Host URI` to `tcp://docker:2376` and then run a Test Connection. Unfortunately, you will see the following (misleading?) error — "_Client sent an HTTP request to an HTTPS server_" — to solve this we need to set up the Server Credentials.

(if you decided to skip TLS by setting DOCKER_TLS_CERTDIR to blank and using port 2375, your test should have worked and you will be able to jump over the next steps).

![](https://i.imgur.com/uPgKXX0.png)

Click on the `Add` drop down and create an `X.509 Client Certificate`.

Remember that /certs directory? At start-up [`jenkins-docker`][dsuite-jenkins-docker]  (a Docker-in-Docker container) generates client/server keys and we need those information to successfully talk to it over TLS.

1. Client Key.
2. Client Certificate.
3. Server CA Certificate.

We can obtain those by running the following commands:

```bash
docker exec JenkinsDocker cat /certs/client/key.pem

docker exec JenkinsDocker cat /certs/client/cert.pem

docker exec JenkinsDocker cat /certs/server/ca.pem
```

After creating your Credential, repeat the Test Connection and you should now be successful.

![](https://i.imgur.com/Q77A1d3.png)

This is great — now we can reach the Docker daemon.


## ![](https://github.com/docker-suite/artwork/raw/master/various/pin/png/pin_16.png) Configuring Docker Agent template

Next step is to set up an agent to run our pipelines against the docker cloud.
Here we will be using [`dsuite/jenkins-agent`](dsuite-jenkins-agent) which embed docker-cli to communicate with the docker cloud.

1. Set agent label and name 
2. use `dsuite/jenkins-agent` as docker image
3. then set the remote file system root to `/home/jenkins/agent`

![](https://i.imgur.com/VtFl5L1.png)

The **Connect Method** will be **Attach Docker Container**. This runs the Docker container on the host machine (in our case, our host is [`jenkins-docker`][dsuite-jenkins-docker] so that means we are running agents inside the Docker-in-Docker container).

At this point the agent could be used, however you won't be able to run commands such as sh `'docker pull alpine'` as you will get :

![](https://i.imgur.com/R58JS94.png)

To address this problem we need to go dipper in the agent configuration and mount `/var/run/docker.sock` between our host and the agent. You can also mount `/home/jenkins/agent` if you want to preserve the agent workspace.

![](https://i.imgur.com/nRmkvWb.png)

Save everything and you are now ready to create a test job.


## ![](https://github.com/docker-suite/artwork/raw/master/various/pin/png/pin_16.png) A test job to verify our jenkins setup

To check that all is set up and working correctly, create a new pipeline job and use the following pipeline :

```groovy
pipeline {

    agent { label 'docker-agent' }

    stages {
        stage('Docker test') {
            steps {
                sh 'docker pull alpine'
            }
        }
    }
}
```

Save and build your job. Jenkins will download the agent for your container and run the job.<br>On completion you should get output such as shown below.

![](https://i.imgur.com/4npCSMd.png)

All done. Success!<br>You are ready to go and build great jobs.


[jenkins]: https://jenkins.io/
[dsuite-jenkins]: https://github.com/docker-suite/jenkins/
[dsuite-jenkins-docker]: https://github.com/docker-suite/jenkins-docker/
[dsuite-jenkins-agent]: https://github.com/docker-suite/jenkins-agent/
