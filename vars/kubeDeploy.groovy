import com.ibm.samples.jenkins.GlobalVars

def call(String fullImageTag, String imageName, String imageTag, String kubeDeploymentName, String kubeNamespace, String containerName, String configMapTruststore, String configMapAppProperties, String kubeSecretTruststore, Boolean recycleDeployment) {
	echo "KubeDeploy ..."
	try {
          container('kubectl') {
			//echo "imageTag = ${imageTag}"
			//echo "gitCommit = ${gitCommit}"
            //def fullImageTag = imageTag
			//imageTag = gitCommit
			containerName = kubeDeploymentName
            echo "fullImageTag = ${fullImageTag}"
			echo "imageName = ${imageName}"
            echo "imageTag = ${imageTag}"
			echo "kubeDeploymentName = ${kubeDeploymentName}"
			echo "kubeNamespace = ${kubeNamespace}"
			echo "containerName = ${containerName}"
			echo "configMapTruststore = ${configMapTruststore}"
			echo "configMapAppProperties = ${configMapAppProperties}"
			echo "kubeSecretTruststore = ${kubeSecretTruststore}"
			echo "recycleDeployment = ${recycleDeployment}"
            sh """
            #!/bin/bash
            echo "checking if ${kubeDeploymentName} deployment already exists"
            if kubectl describe deployment ${kubeDeploymentName} --namespace ${kubeNamespace}; then
                echo "Application already exists, update..."
                kubectl set image deployment/${kubeDeploymentName} ${containerName}=${fullImageTag} --namespace ${kubeNamespace}
            else
                sed -i "s/<KUBE_TOKEN_01>/${kubeDeploymentName}/g" kube-artifacts/kube.deploy.yaml
                sed -i "s/<KUBE_TOKEN_02>/${kubeDeploymentName}/g" kube-artifacts/kube.deploy.yaml
                sed -i "s/<KUBE_TOKEN_03>/${kubeDeploymentName}/g" kube-artifacts/kube.deploy.yaml
                sed -i "s/<KUBE_TOKEN_04>/${kubeDeploymentName}/g" kube-artifacts/kube.deploy.yaml
				sed -i "s/<KUBE_DEPLOYMENT_NAME>/${kubeDeploymentName}/g" kube-artifacts/kube.deploy.yaml            
                sed -i "s/<CONTAINER_NAME>/${containerName}/g" kube-artifacts/kube.deploy.yaml
                sed -i "s/<DOCKER_IMAGE>/${imageName}:${imageTag}/g" kube-artifacts/kube.deploy.yaml
                sed -i "s/<CONFIGMAP_TRUSTSTORE>/${configMapTruststore}/g" kube-artifacts/kube.deploy.yaml            
                sed -i "s/<CONFIGMAP_APP_PROPERTIES>/${configMapAppProperties}/g" kube-artifacts/kube.deploy.yaml
				sed -i "s/<SECRET_TRUSTSTORE>/${kubeSecretTruststore}/g" kube-artifacts/kube.deploy.yaml            

                cat kube-artifacts/kube.deploy.yaml
                echo "Create ConfigMap ... TODO ..."
                echo "Create deployment"
                kubectl apply -f kube-artifacts/kube.deploy.yaml --namespace ${kubeNamespace}
            fi
            echo "Describe deployment"
            kubectl describe deployment ${kubeDeploymentName} --namespace ${kubeNamespace}
            echo "finished"
            """
          }

	} catch (err) {
		echo "An error occurred: " + err.message
		currentBuild.result = 'FAILURE'
		throw(err)
	}
}
