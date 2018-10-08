import com.ibm.samples.jenkins.GlobalVars

def call(String commitId, String deploymentEnvironment, Boolean pushArtifact, String applicationName, String componentName, String deployProcess) {

    try {

        // TODO: Refactor out all these variables to make parameters so that this
        // groovy script can be used for all ucd deployments!

        def imageTag = "";
        def IMAGE_TAG
        def TAG_NEW_String

        def OFFSET_DIR="chart/" + componentName
        def TARGET_FILE="values.yaml"
        def BUILD_PROPERTIES_FILE="build.properties"
        def TAG_OLD_String="@@@TAG@@@"
      
        def UCD_APP_NAME = applicationName
        def UCD_COMPONENT_NAME = componentName 
        def UCD_COMPONENT_TEMPLATE = "HelmChartTemplate"
        def UCD_DELIVERY_BASE_DIR = null
        def UCD_DELIVERY_PUSH_VERSION = null       
        def UCD_Deploy_Env = deploymentEnvironment
        def UCD_Deploy_Process = deployProcess
        def UCD_Deploy_Version = null   

        imageTag = commitId
        IMAGE_TAG = commitId
        TAG_NEW_String=commitId
            
        UCD_DELIVERY_BASE_DIR = WORKSPACE + "/" + OFFSET_DIR
        UCD_DELIVERY_PUSH_VERSION = BRANCH_NAME + "." + BUILD_NUMBER
        UCD_Deploy_Version = UCD_COMPONENT_NAME + ":" + BRANCH_NAME + "." + BUILD_NUMBER
        TARGET_FILE =  UCD_DELIVERY_BASE_DIR + "/" + TARGET_FILE
         
        sh """
        #!/bin/bash
        pwd
        ls -l
        echo "imageTag: ${imageTag}"
        echo "BUILD_NUMBER: ${BUILD_NUMBER}"
        echo "WORKSPACE: ${WORKSPACE}"
        echo "UCD_APP_NAME = ${UCD_APP_NAME}"
        echo "UCD_COMPONENT_NAME = ${UCD_COMPONENT_NAME}"
        echo "UCD_Deploy_Env = ${UCD_Deploy_Env}"
        echo "UCD_DELIVERY_BASE_DIR = ${UCD_DELIVERY_BASE_DIR}"
        echo "UCD_DELIVERY_PUSH_VERSION = ${UCD_DELIVERY_PUSH_VERSION}"
        echo "UCD_Deploy_Process = ${UCD_Deploy_Process}"
        echo "UCD_Deploy_Version = ${UCD_Deploy_Version}"
        echo "TARGET_FILE = ${TARGET_FILE}"
        echo "-------------------------"
        echo "Verify target file: ${TARGET_FILE}"
        ls -l ${TARGET_FILE}           				
        echo "update TAG before: "
        cat ${TARGET_FILE} | grep tag:						          		
        sed -i "s|@@@TAG@@@|${imageTag}|g" ${TARGET_FILE}	          		
        echo "update TAG before: "
        cat ${TARGET_FILE} | grep tag:	          			        	    		        	    	
        """

        if (pushArtifact) {
            step([$class: 'UCDeployPublisher',
            siteName: 'UCD-Server',
            component: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.VersionHelper$VersionBlock',
                componentName: "${UCD_COMPONENT_NAME}",
                delivery: [
                    $class: 'com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper$Push',
                    pushVersion: "${UCD_DELIVERY_PUSH_VERSION}",
                    baseDir: "${UCD_DELIVERY_BASE_DIR}",
                    fileIncludePatterns: '/**',
                    fileExcludePatterns: '',

                    pushDescription: 'Pushed from Jenkins',
                    pushIncremental: false
                ]
            ]                      
            ])
        }
                    
        step([$class: 'UCDeployPublisher',
            siteName: 'UCD-Server',                       
            deploy: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.DeployHelper$DeployBlock',
                deployApp: "${UCD_APP_NAME}",
                deployEnv: "${UCD_Deploy_Env}",
                deployProc: "${UCD_Deploy_Process}",
                deployVersions: "${UCD_Deploy_Version}",
                deployOnlyChanged: false
            ]                        
        ])         
    } catch (err) {
        echo "An error occurred: " + err.message
        currentBuild.result = 'FAILURE'
        throw(err)
    }
}
