def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
	body()

	def COMMON_PIPELINE_01_VERSION = "10-15-2018"
	def DEPLOYMENT_METHOD = config.deploymentMethod
	
	def APPLICATION_NAME = config.applicationName
	def COMPONENT_NAME = config.componentName
	def DEPLOY_PROCESS = "Deploy-${COMPONENT_NAME}"
	def UCD_Env = config.ucdEnv
	def HELM_CHART_TEMPLATE = config.helmChartTemplate
	def KUBE_DEPLOYMENT_TEMPLATE = config.kubeDeploymentTemplate
	def kubeNamespace = config.kubeNamespaceForDeployment	
	def kubeDeploymentName = COMPONENT_NAME	
	def imageNameSpace = kubeNamespace
	def helmChartName = kubeDeploymentName
	def containerName = kubeDeploymentName
	def imageName = kubeDeploymentName
	def imageTag
	def gitCommit
	def label = "${kubeDeploymentName}-${UUID.randomUUID().toString()}"
	def volumes = [ hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'), 
				 	hostPathVolume(hostPath: '/tmp', mountPath: '/home/gradle/.gradle') ]			 	
	volumes += secretVolume(secretName: 'jenkins-docker-sec', mountPath: '/jenkins_docker_sec')
	podTemplate(label: label, slaveConnectTimeout: 600, runAsUser: 0, fsGroup: 0,
	    containers: [
	        containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:3.23-1', args: '${computer.jnlpmac} ${computer.name}'),
	        containerTemplate(name: 'gradle', image: 'ibmcheng1/mygradle:4.10.1-jdk8', command: 'cat', ttyEnabled: true),
	        containerTemplate(name: 'docker', image: 'docker:17.12', ttyEnabled: true, command: 'cat'),
	        containerTemplate(name: 'kubectl', image: 'ibmcom/k8s-kubectl:v1.8.3', ttyEnabled: true, command: 'cat'),
	    ],
	    volumes: volumes
	) {

        node (label) {
        
            //Clean the workspace first to ensure happyness
            deleteDir()

            try {
			  echo "+++++ LIBRARY START +++++ CommonPipeline01: " + COMMON_PIPELINE_01_VERSION
			  echo "APPLICATION_NAME=" + APPLICATION_NAME + ", COMPONENT_NAME=" + COMPONENT_NAME + ", DEPLOY_PROCESS=" + DEPLOY_PROCESS + ", UCD_Env=" + UCD_Env + ", HELM_CHART_TEMPLATE=" + HELM_CHART_TEMPLATE + ", KUBE_DEPLOYMENT_TEMPLATE=" + KUBE_DEPLOYMENT_TEMPLATE + ", kubeNamespace=" + kubeNamespace  ", DEPLOY_BY_KUBE=" + DEPLOY_BY_KUBE
			  
			  //if(env.GIT_BRANCH != 'master') {
			  //	echo "This branch is ineligible for production."
			  //}
			  				
			  stage('build-ext-01') {
					git branch: 'master',
					   credentialsId: '6e1534b2-15e4-49f7-8735-f41c44334547',
					   url: 'git@github.ibm.com:MICROSERVICES-STT/tradebook-build-ext.git'
					sh """
		        	pwd
		        	rm README.md
		        	rm -rf .git
		        	ls -lat
		        	mkdir ../tmp
		        	cp -rf ./* ../tmp/ 
					ls -lat
					ls -lat ../tmp/
					"""
			   }
		   
			   stage ('Git') {
				 checkout scm
				 gitCommit = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
				 echo "Stage-git: checked out git commit ${gitCommit}"
			   }
	   
			   stage ('Gradle') {
				 container('gradle') {
				   sh '''
				   echo "Stage-gradle: build ..."
				   pwd
				   chmod +x gradlew
				   ./gradlew clean build test --refresh-dependencies
				   '''
				 }
			   }
	   
				stage ('Docker') {
				 container('docker') {
				   echo "Stage-docker: docker - build tag push ..."
				   imageTag = "mycluster.icp:8500/${kubeNamespace}/${imageName}:${gitCommit}"
				   echo "imageTag = ${imageTag}"
				   sh """
				   ln -s /jenkins_docker_sec/.dockercfg /home/jenkins/.dockercfg
				   mkdir /home/jenkins/.docker
				   ln -s /jenkins_docker_sec/.dockerconfigjson /home/jenkins/.docker/config.json
				   docker build -t ${imageName} .
				   docker tag ${imageName} $imageTag
				   docker push $imageTag
				   """
				 }
			   }
			   
			   stage('build-ext-02') {
					sh """
				   pwd
				   ls -lat
				   ls -lat ../tmp/
				   cp -rf ../tmp/* .
				   ls -lat
				   """
			   }
									
			   stage ('Deploy') {
				   def fullImageTag = imageTag
				   imageTag = gitCommit
				   echo "fullImageTag = ${fullImageTag}"
				   echo "imageTag = ${imageTag}"
				   echo "helmChartName = ${helmChartName}"
				   echo "imageNameSpace = ${imageNameSpace}"
				   echo "imageName = ${imageName}"
				   
				   echo "COMPONENT_NAME = ${COMPONENT_NAME}"
				   echo "HELM_CHART_TEMPLATE = ${HELM_CHART_TEMPLATE}"
				   echo "KUBE_DEPLOYMENT_TEMPLATE = ${KUBE_DEPLOYMENT_TEMPLATE}"
				   
				   sh """
				   #!/bin/bash
				   pwd
					ls -l
					mv chart/${HELM_CHART_TEMPLATE} chart/${COMPONENT_NAME}
					ls -l chart
								
				   sed -i "s/<HELM_CHART_NAME>/${helmChartName}/g" chart/${COMPONENT_NAME}/Chart.yaml
				   sed -i "s/<IMAGE_NAMESPACE>/${imageNameSpace}/g" chart/${COMPONENT_NAME}/values.yaml
				   sed -i "s/<IMAGE_NAME>/${imageName}/g" chart/${COMPONENT_NAME}/values.yaml
				   sed -i "s/<IMAGE_TAG>/${imageTag}/g" chart/${COMPONENT_NAME}/values.yaml
				   cat chart/${COMPONENT_NAME}/Chart.yaml
				   cat chart/${COMPONENT_NAME}/values.yaml
				   
				   cp -r kube-deployment/${KUBE_DEPLOYMENT_TEMPLATE} .
					mv ${KUBE_DEPLOYMENT_TEMPLATE} kube-artifacts
					ls -l .
					ls -l kube-artifacts
					cat kube-artifacts/kube.deploy.yaml
				   """
				   
				   if (DEPLOYMENT_METHOD == 'kube') {
					   echo 'User configured to deploy via Kubectl'
				   } else if (DEPLOYMENT_METHOD == 'helm') {
					   echo 'User configured to deploy via helm. Not Supported.'
				   } else {
					   echo 'default to UCD deployment'
				   }
				   
				   kubeDeploy(fullImageTag, imageTag, kubeDeploymentName, kubeNamespace, containerName, false)
				   
				   //ucdDeploy(gitCommit, UCD_Env, true, APPLICATION_NAME, COMPONENT_NAME, DEPLOY_PROCESS)
				   
				   echo "+++++ LIBRARY END +++++"
			   }
				
				
            } catch (err) {
                echo "The system is not responding or is experiencing technical difficulties."
                echo "+++++ LIBRARY END +++++"
                currentBuild.result = 'FAILED'
                throw err
            }
        }
    }
}