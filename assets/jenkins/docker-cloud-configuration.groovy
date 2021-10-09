#!groovy

/////////////////////////////////////////////////////////////////////////////
// THis code is based on the docker-plugin configuration script.
// Cf. https://github.com/jenkinsci/docker-plugin/blob/master/docs/attachments/docker-plugin-configuration-script.groovy
/////////////////////////////////////////////////////////////////////////////

import com.nirima.jenkins.plugins.docker.DockerCloud
import com.nirima.jenkins.plugins.docker.DockerDisabled
import com.nirima.jenkins.plugins.docker.DockerImagePullStrategy
import com.nirima.jenkins.plugins.docker.DockerTemplate
import com.nirima.jenkins.plugins.docker.DockerTemplateBase
import com.nirima.jenkins.plugins.docker.launcher.AttachedDockerComputerLauncher
import hudson.slaves.Cloud
import io.jenkins.docker.client.DockerAPI
import io.jenkins.docker.connector.DockerComputerAttachConnector
import io.jenkins.docker.connector.DockerComputerConnector
import jenkins.model.Jenkins
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint


/////////////////////////////////////////////////////////////////////////////
// Parameters in this script are placed in groovy maps.
// Most parameters are strings in 'single quotes'.
// Some are boolean, so either true or false.
// Some are numbers, either Integer or Long.
// Most parameters are optional.
// Default values are usually an empty string, false, or no number set.
/////////////////////////////////////////////////////////////////////////////

// Parameters listed here are used to create a
// https://github.com/jenkinsci/docker-plugin/blob/master/src/main/java/io/jenkins/docker/client/DockerAPI.java
// and
// https://github.com/jenkinsci/docker-plugin/blob/master/src/main/java/com/nirima/jenkins/plugins/docker/DockerCloud.java
 def cloudParameters = [
  // serverUrl and name are required.
  // everything else is optional
  name:                         'local-docker-cloud',
  serverUrl:                    'tcp://docker:2375',
  containerCap:                 4, // 0 means no cap
  // credentialsId:             '',
  // connectTimeout:            5,
  // readTimeout:               60,
  // version:                   '',
  // dockerHostname:            '',
  // exposeDockerHost:          false,
  // disabled:                  false,
  // errorDuration:             (Integer)null,
]

// Parameters listed here are used to create a
// https://github.com/jenkinsci/docker-plugin/blob/master/src/main/java/com/nirima/jenkins/plugins/docker/DockerTemplateBase.java
def templateBaseParameters = [
  image:                        'dsuite/jenkins-agent',
  // all other parameters are optional
  // Uncomment them if you want to set them.
  // bindAllPorts:              false,
  // bindPorts:                 '',
  // capabilitiesToAddString:   '',
  // capabilitiesToDropString:  '',
  // cpuPeriod:                 (Long)null,
  // cpuQuota:                  (Long)null,
  // cpuShares:                 (Integer)null,
  // devicesString:             '',
  // dnsString:                 '',
  // dockerCommand:             '',
  // environmentsString:        '',
  // extraDockerLabelsString:   '',
  // extraGroupsString:         '',
  // extraHostsString:          '',
  hostname:                     'docker-agent',
  // macAddress:                '',
  // memoryLimit:               (Integer)null,
  // memorySwap:                (Integer)null,
  // network:                   '',
  // privileged:                false,
  // pullCredentialsId:         '',
  // securityOptsString:        '',
  // shmSize:                   (Integer)null,
  tty:                          true,
  // user:                      '',
  // volumesFromString:         '',
  mountsString:                 'type=bind,source=/var/run/docker.sock,destination=/var/run/docker.sock\ntype=bind,source=/home/jenkins/agent,destination=/home/jenkins/agent',
]

// Parameters listed here are used to create a
// https://github.com/jenkinsci/docker-plugin/blob/master/src/main/java/com/nirima/jenkins/plugins/docker/DockerTemplate.java
def templateParameters = [
  // all parameters except name, remoteFs and labelString are optional
  // disabled:                  false,
  // instanceCapStr:            '4',
  labelString:                  'docker-agent',
  // mode:                      hudson.model.Node.Mode.NORMAL,
  name:                         'docker-agent',
  pullStrategy:                 DockerImagePullStrategy.PULL_ALWAYS,
  pullTimeout:                  300,
  remoteFs:                     '/home/jenkins/agent',
  //removeVolumes:              false,
  // stopTimeout:               10,
]


/////////////////////////////////////////////////////////////////////////////
// The code above defines our data.
// Now to turn that raw data into objects used by the
// docker-plugin code...
/////////////////////////////////////////////////////////////////////////////


/////////////////////////////////////////////////////////////////////////////
// Create docker cloud
/////////////////////////////////////////////////////////////////////////////

Set<String> cloudParametersHandledSpecially = [ 'serverUrl', 'credentialsId' ,'serverUrl' ,'credentialsId' ,'connectTimeout' ,'readTimeout' ,'version' ,'connectTimeout' ,'dockerHostname' ,'name' ]
DockerAPI api = new DockerAPI(new DockerServerEndpoint(cloudParameters.serverUrl, cloudParameters.credentialsId))
api.with {
  connectTimeout = cloudParameters.connectTimeout
  readTimeout = cloudParameters.readTimeout
  apiVersion = cloudParameters.version
  hostname = cloudParameters.dockerHostname
}
DockerCloud newCloud = new DockerCloud(
  cloudParameters.name,
  api,
  null
)
cloudParameters.findAll{!cloudParametersHandledSpecially.contains(it.key)}.each { k, v ->
  if ( k=="disabled" ) {
    DockerDisabled dd = new DockerDisabled()
    dd.disabledByChoice = v
    newCloud."$k" = dd
  } else {
    newCloud."$k" = v
  }
}


/////////////////////////////////////////////////////////////////////////////
// docker agent template
/////////////////////////////////////////////////////////////////////////////

DockerTemplateBase templateBase = new DockerTemplateBase(templateBaseParameters.image)
templateBaseParameters.findAll{ it.key != "image" }.each { k, v ->
  templateBase."$k" = v
}
DockerComputerConnector computerConnector = new DockerComputerAttachConnector()
Set<String> templateParametersHandledSpecially = [ 'labelString', 'instanceCapStr' ]
DockerTemplate template = new DockerTemplate(
  templateBase,
  computerConnector,
  templateParameters.labelString,
  templateParameters.instanceCapStr
)
templateParameters.findAll{ !templateParametersHandledSpecially.contains(it.key) }.each { k, v ->
  if ( k=="disabled" ) {
    DockerDisabled dd = new DockerDisabled()
    dd.disabledByChoice = v
    template."$k" = dd
  } else {
    template."$k" = v
  }
}
newCloud.addTemplate(template)

/////////////////////////////////////////////////////////////////////////////
// Now to push our data into Jenkins,
// replacing (overwriting) any cloud of the same name with this config.
/////////////////////////////////////////////////////////////////////////////

// get Jenkins instance
Jenkins jenkins = Jenkins.get()

// add/replace cloud configuration to Jenkins
Cloud oldCloudOrNull = jenkins.clouds.getByName(cloudParameters.name)
if ( oldCloudOrNull ) {
  jenkins.clouds.remove(oldCloudOrNull)
}
jenkins.clouds.add(newCloud)

// save current Jenkins state to disk
jenkins.save()

/////////////////////////////////////////////////////////////////////////////
// ...and we're done.
/////////////////////////////////////////////////////////////////////////////
