def BRANCH
def ECR_ACCOUNT
def NOTIFY
def PUSH_TO_ECR
def TYPE
def VERSION

// Requirements:
// - Make sure the Persistent Parameter plugin is installed (https://plugins.jenkins.io/persistent-parameter/)

// Persistent Parameter declarative pipeline "documentation"
// https://github.com/jenkinsci/persistent-parameter-plugin/pull/12

pipeline {
    agent {
        label 'docker'
    }

    parameters {
        choice(name: 'TYPE', choices: ['Release', 'Debug'], description: 'Build configuration to use.')
        persistentString(name: 'VERSION', trim: true, description: 'Version number, used in the image name')
        string(name: 'BRANCH', defaultValue: 'dev', trim: true, description: 'Git branch to build. Case is most likely important')
        
        booleanParam(name: 'PUSH_TO_ECR', defaultValue: true, description: 'Whether or not to push the built image to the container registry (Amazon ECR)')
        choice(name: 'ECR_ACCOUNT', choices: ['Development'/*, 'Staging', 'QA', 'Live'*/], description: 'If PUSH_TO_ECR is set, specifies on which AWS account registry the image will be pushed.')

        booleanParam(name: 'NOTIFY', defaultValue: true, description: 'If set, build notification are sent to Discord.')
    }

    stages {
        stage('Declarations') {
            steps {
                script {
                    cmd = { String code ->
                        if (isUnix()) {
                            sh(code)
                        } else {
                            bat(code)
                        }
                    }
                    cmdStd = { String code ->
                        if (isUnix()) {
                            return sh(script: code, returnStdout: true)
                        } else {
                            return bat(script: code, returnStdout: true)
                        }
                    }
                    cmdTry = { String code ->
                        try {
                            if (isUnix()) {
                                sh(code)
                            } else {
                                bat(code)
                            }
                        } catch (err) {
                            echo err.getMessage()
                        }
                    }

                    switch (params.ECR_ACCOUNT) {
                        case "Development":
                            awsAccountId = "248189945296"
                            break
                        case "Staging":
                            awsAccountId = "242201270483"
                            break
                        case "QA":
                            awsAccountId = "202533543681"
                            break
                        case "Live":
                            awsAccountId = "120569624858"
                            break
                    }

                    ecrBaseUrl = "${awsAccountId}.dkr.ecr.eu-west-1.amazonaws.com"
                    imageName = "tinywizard/server"

                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    BUILD_USER = cause.userName
                    BUILD_DISPLAY_NUMBER = BUILD_NUMBER.toString().padLeft(5, "0")

                    if (!params.VERSION) {
                        error "Build failed: No value provided for the version parameter."
                    }
                }
            }
        }
        stage('ECR authentication') {
            when {
                expression { return params.PUSH_TO_ECR }
            }
            steps {
                cmd "aws ecr get-login-password --region eu-west-1 | sudo docker login --username AWS --password-stdin ${ecrBaseUrl}"
            }
        }
        stage('Getting latest') {
            steps {
                script {
                    if(params.NOTIFY) {
                        discordSend description: "*${BUILD_USER}* started a Docker image build\n" +
                            "**Image**: ${imageName}\n" +
                            "**Branch**: ${params.BRANCH}\n" +
                            "**Build name**: ${BUILD_DISPLAY_NUMBER}\n",
                            link: env.BUILD_URL,
                            result: "ABORTED",
                            title: "[${BUILD_DISPLAY_NUMBER}] Build start ${imageName} ${params.TYPE} (${params.BRANCH})",
                            webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                    }

                    cmd "git config --global --add safe.directory '*'"
                    try {
                        cmd "git status"
                    } catch (err) {
                        echo "Git repository does not exist. Cloning..."
                        deleteDir()
                        withCredentials([sshUserPrivateKey(credentialsId: 'GITHUB_SSH', keyFileVariable: 'SSH_KEY_FILE')]) {
                            cmd "GIT_SSH_COMMAND=\"ssh -i '$SSH_KEY_FILE' -o StrictHostKeyChecking=no\" git clone --depth 1 -b \"${params.BRANCH}\" git@github.com:tinywizardgames/tinyWizardRoyale.git ."
                        }
                    }
                    cmd "git config remote.origin.fetch +refs/heads/${params.BRANCH}:refs/remotes/origin/${params.BRANCH}"
                    withCredentials([sshUserPrivateKey(credentialsId: 'GITHUB_SSH', keyFileVariable: 'SSH_KEY_FILE')]) {
                        cmd "GIT_SSH_COMMAND=\"ssh -i '$SSH_KEY_FILE' -o StrictHostKeyChecking=no\" git remote update"
                        cmd "GIT_SSH_COMMAND=\"ssh -i '$SSH_KEY_FILE' -o StrictHostKeyChecking=no\" git fetch --depth 1 origin \"${params.BRANCH}\""
                        cmd "GIT_SSH_COMMAND=\"ssh -i '$SSH_KEY_FILE' -o StrictHostKeyChecking=no\" git reset --hard \"origin/${params.BRANCH}\""
                        cmd "git clean -df"
                        cmd "GIT_SSH_COMMAND=\"ssh -i '$SSH_KEY_FILE' -o StrictHostKeyChecking=no\" git lfs install"
                        cmd "GIT_SSH_COMMAND=\"ssh -i '$SSH_KEY_FILE' -o StrictHostKeyChecking=no\" git lfs fetch"
                    }

                    GIT_COMMIT = cmdStd('git rev-parse --short HEAD').trim()
                    echo "Commit: ${GIT_COMMIT}"
                }
            }
        }
        stage('Build') {
            steps {
                script {
                    buildTime = new Date(currentBuild.getStartTimeInMillis()).format("yyyy-MM-dd'T'HH:mm:ssXXX", TimeZone.getTimeZone("UTC"))
                    cmd("sudo docker build --build-arg BUILD_CONFIGURATION=${params.TYPE} --build-arg BUILDTIME=${buildTime} --build-arg REVISION=${GIT_COMMIT} -f Dockerfile.server -t ${imageName}:build${BUILD_NUMBER} .")
                }
            }
        }
        stage('Push to ECR') {
            when {
                expression { return params.PUSH_TO_ECR }
            }
            steps {
                script {
                    imageTags = [
                        "${ecrBaseUrl}/${imageName}:${params.VERSION}.${BUILD_NUMBER}",
                    ]
                    
                    switch (params.ECR_ACCOUNT) {
                        case "Development":
                            if (params.BRANCH == "dev") {
                                imageTags << "${ecrBaseUrl}/${imageName}:latest"
                            }
                            break
                        case "Staging":
                            break
                        case "QA":
                            break
                        case "Live":
                            break
                    }

                    // Apply all tags to this build
                    imageTags.each { value ->
                    }

                    // Push image and all its tags to the repository
                    imageTags.each { value ->
                        cmd("sudo docker image tag ${imageName}:build${BUILD_NUMBER} ${value}")
                        cmd("sudo docker image push ${value}")
                    }

                    if(params.NOTIFY) {
                        discordSend description: "Docker image has been pushed to ${params.ECR_ACCOUNT} ECR registry\n" +
                            "**Image**: ${imageName}\n" +
                            "**Tags**:\n" +
                            imageTags.collect { "  - $it" }.join("\n") + "\n" +
                            "**Branch**: ${params.BRANCH}\n" +
                            "**Build name**: ${BUILD_DISPLAY_NUMBER}\n",
                            link: env.BUILD_URL,
                            result: "ABORTED",
                            title: "[${BUILD_DISPLAY_NUMBER}] Build start ${imageName} ${params.TYPE} (${params.BRANCH})",
                            webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                cmdTry "sudo docker image prune -a --force --filter \"until=336h\"" // Clean images older than 14 days
            }
        }

        success {
            script {
                if(params.NOTIFY) {
                    discordSend description: "*${BUILD_USER}* Docker image build successful\n" +
                        "**Image**: ${imageName}\n" +
                        "**Branch**: ${params.BRANCH}\n" +
                        "**Build name**: ${BUILD_DISPLAY_NUMBER}\n",
                        link: env.BUILD_URL,
                        result: currentBuild.currentResult,
                        title: "[${BUILD_DISPLAY_NUMBER}] Build success ${imageName} ${params.TYPE} (${params.BRANCH})",
                        webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                }
            }
        }

        failure {
            script {
                if(params.NOTIFY) {
                    discordSend description: "*${BUILD_USER}* Docker image build failed\n" +
                        "**Image**: ${imageName}\n" +
                        "**Branch**: ${params.BRANCH}\n" +
                        "**Build name**: ${BUILD_DISPLAY_NUMBER}\n",
                        link: env.BUILD_URL,
                        result: currentBuild.currentResult,
                        title: "[${BUILD_DISPLAY_NUMBER}] Build failure ${imageName} ${params.TYPE} (${params.BRANCH})",
                        webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                }
            }
        }

        aborted {
            script {
                if(params.NOTIFY) {
                    discordSend description: "*${BUILD_USER}* Docker image build failed\n" +
                        "**Image**: ${imageName}\n" +
                        "**Branch**: ${params.BRANCH}\n" +
                        "**Build name**: ${BUILD_DISPLAY_NUMBER}\n",
                        link: env.BUILD_URL,
                        result: currentBuild.currentResult,
                        title: "[${BUILD_DISPLAY_NUMBER}] Build abort ${imageName} ${params.TYPE} (${params.BRANCH})",
                        webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                }
            }
        }
    }
}