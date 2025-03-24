pipeline {
    parameters {
        choice(name: 'TYPE', choices: ['WindowsDevelop', 'WindowsRelease', 'WindowsStaging', 'AndroidDevelop', 'AndroidRelease', 'AndroidStaging', 'IOSDevelop', 'IOSRelease', 'IOSStaging']) // 'LinuxDevelop', 'LinuxRelease', 'LinuxStaging'
        string(name: 'BRANCH', defaultValue: 'main', trim: true, description: "Case is most likely important.")
        choice(name: 'SCRIPTING', choices: ['IL2CPP', 'Default', 'Mono2x'], description: "This overrides the scripting backend. Default will use whatever is in the build configs.")
        choice(name: 'SUBTARGET', choices: ['Player', 'Server'])
        booleanParam(name: 'BUILD', defaultValue: true, description: 'Whether or not to build the unity project.')
        booleanParam(name: 'ADDRESSABLES', defaultValue: true, description: 'Whether or not to build and upload addressables.')
        persistentString(name: 'ADDRESSABLE_URL', defaultValue: "https://twbr-addressables.s3.eu-west-2.amazonaws.com", successfulOnly: false)
        persistentString(name: 'ADDRESSABLE_VERSION', defaultValue: "000001", description: 'Only update the version when addressable changes are made that are not backwards compatible.', successfulOnly: false)
        booleanParam(name: 'DEEP_PROFILING_SUPPORT', defaultValue: false, description: 'Whether or not to add support for deep profiling to the build.')
        booleanParam(name: 'WAIT_FOR_DEBUGGER', defaultValue: false, description: 'The build will wait to start until a debugger is attached or you click continue. Allows you to debug the start of the game.')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Deploys to the store. Does not submit for review or set live.')
        booleanParam(name: 'CLEAN', defaultValue: false, description: 'Rebuilds the library. Will take a long time.')
        booleanParam(name: 'NOTIFY', defaultValue: true, description: 'If true, build notifications are sent to discord.')
        booleanParam(name: 'POST_CLEANUP', defaultValue: true, description: 'If true, removes leftover artifacts and cache files after a build.')
    }
    agent none
    options {
        timestamps()
        //timeout(time: 120, unit: 'MINUTES')
        ansiColor('xterm')
    }
    stages {
        stage('Getting Latest') {
            environment {
                LC_ALL = "en_US.UTF-8"
                LANGUAGE = "en_US.UTF-8"
                LANG = "en_US.UTF-8"
                PATH = "${getPath()}"
            }
            steps {
                script {
                    echo PATH

                    BUILD_SCRIPTING = params.SCRIPTING
                    if(isMac() && params.SCRIPTING == "IL2CPP" && params.TYPE.startsWith("Windows")) {
                        echo "IL2CPP for windows is not supported on Mac. Using Mono instead."
                        BUILD_SCRIPTING = "Mono2x"
                    }

                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    BUILD_USER = cause.userName
                    BUILD_DISPLAY_NUMBER = BUILD_NUMBER.toString().padLeft(5, "0")
                    def branchName = params.BRANCH.replace("dev-TWBR-", "").replace("-", "_")
                    if(branchName == "main") {
                        currentBuild.displayName = "JumpPlayground${params.TYPE}_${BUILD_DISPLAY_NUMBER}"
                    } else {
                        currentBuild.displayName = "JumpPlayground${branchName}_${params.TYPE}_${BUILD_DISPLAY_NUMBER}"
                    }
                    if(params.NOTIFY) {
                        if(params.SUBTARGET == "Player") {
                            discordSend description: "*${BUILD_USER}* started a unity player build.\n" +
                                    "**Build Name**: ${currentBuild.displayName}\n" +
                                    "**Branch**: ${params.BRANCH}\n" +
                                    "**Platform**: ${params.TYPE} ${params.SUBTARGET}", 
                                    link: env.BUILD_URL, 
                                    result: "ABORTED", 
                                    title: "[${BUILD_DISPLAY_NUMBER}] Build Started ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                    webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                        } else {
                            discordSend description: "*${BUILD_USER}* started a unity server build.\n" +
                                    "**Build Name**: ${currentBuild.displayName}\n" +
                                    "**Branch**: ${params.BRANCH}\n" +
                                    "**Platform**: ${params.TYPE} ${params.SUBTARGET}", 
                                    link: env.BUILD_URL, 
                                    result: "ABORTED", 
                                    title: "[${BUILD_DISPLAY_NUMBER}] Build Started ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                    webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                        }
                    }
                    
                    if(isMac()) {
                        cmd "sudo chown -Rf jenkins:staff ./*"
                    }

                    cmd "git config --global --add safe.directory '*'"
                    try {
                        cmd "git status"
                    } catch (err) {
                        echo "Git repository does not exist. Cloning..."
                        deleteDir()
                        cmd "git clone --depth 1 -b \"${params.BRANCH}\" git@github.com:mkawick/JumpPlayground.git ."
                    }
                    cmd "git config remote.origin.fetch +refs/heads/${params.BRANCH}:refs/remotes/origin/${params.BRANCH}"
                    cmd "git remote update"
                    cmd "git fetch --depth 1 origin \"${params.BRANCH}\""
                    cmd "git reset --hard \"origin/${params.BRANCH}\""
                    cmd "git clean -df"
                    cmd "git lfs install"
                    cmd "git lfs fetch"
                    
                    GIT_COMMIT = cmdStd('git rev-parse --short HEAD').trim()
                    echo "${GIT_COMMIT}"
                }
            }
        }
        stage('Clean') {
            when {
                expression { return params.CLEAN }
            }
            steps {
                script {
                    cmd "git clean -fdx"
                }
            }
        }
        stage('Setup') {
            environment {
                LC_ALL = "en_US.UTF-8"
                LANGUAGE = "en_US.UTF-8"
                LANG = "en_US.UTF-8"
                PATH = "${getPath()}"
            }
            steps {
                script {
                    contents = readFile(file: updatePath("ProjectSettings\\ProjectSettings.asset"))
                    contents = contents.replace("%TAG !u! tag:unity3d.com,2011:\n", "").replaceAll("!u!\\d+\\s+", "")
                    settings = readYaml text: contents
                    GAME_NAME = settings.PlayerSettings.productName.toString().replace(" ", "").replace("-", "")
                    //VERSION = settings.PlayerSettings.bundleVersion.toString()
                    VERSION = BUILD_DISPLAY_NUMBER
                    def branchName = params.BRANCH.replace("dev-TWBR-", "").replace("-", "_")
                    if(branchName == "main") {
                        currentBuild.displayName = "${GAME_NAME}_${params.TYPE}_${BUILD_DISPLAY_NUMBER}"
                    } else {
                        currentBuild.displayName = "${GAME_NAME}_${branchName}_${params.TYPE}_${BUILD_DISPLAY_NUMBER}"
                    }

                    contents = readFile(file: updatePath("ProjectSettings\\ProjectVersion.txt"))
                    versionInfo = readYaml text: contents
                    EDITOR_VERSION = versionInfo.m_EditorVersion?.trim()
                    if(!EDITOR_VERSION) {
                        error("Unable to determine the editor version for the current project.")
                        return
                    }
                    echo "Unity project version ${EDITOR_VERSION} detected."

                    switch(params.TYPE) {
                        case "WindowsDevelop":
                            BUILD_TARGET = "Win64"
                            APP_EXT = "exe"
                            BUILD_NAME = "${GAME_NAME}.${APP_EXT}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildWindowsDevelop"
                            ADDRESSABLES_FOLDER = "StandaloneWindows64"
                            break
                        case "WindowsRelease":
                            BUILD_TARGET = "Win64"
                            APP_EXT = "exe"
                            BUILD_NAME = "${GAME_NAME}.${APP_EXT}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildWindowsRelease"
                            ADDRESSABLES_FOLDER = "StandaloneWindows64"
                            break
                        case "WindowsStaging":
                            BUILD_TARGET = "Win64"
                            APP_EXT = "exe"
                            BUILD_NAME = "${GAME_NAME}.${APP_EXT}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildWindowsStaging"
                            //BUILD_CL = "-buildWindows64Player Builds\\${currentBuild.displayName}\\${GAME_NAME}"
                            ADDRESSABLES_FOLDER = "StandaloneWindows64"
                            break
                        case "AndroidDevelop":
                            BUILD_TARGET = "android"
                            APP_EXT = "apk"
                            BUILD_NAME = "${GAME_NAME}.${APP_EXT}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildAndroidDevelop"
                            ADDRESSABLES_FOLDER = "Android"
                            break
                        case "AndroidRelease":
                            BUILD_TARGET = "android"
                            APP_EXT = "aab"
                            BUILD_NAME = "${GAME_NAME}.${APP_EXT}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildAndroidRelease"
                            ADDRESSABLES_FOLDER = "Android"
                            break
                        case "AndroidStaging":
                            BUILD_TARGET = "android"
                            APP_EXT = "aab"
                            BUILD_NAME = "${GAME_NAME}.${APP_EXT}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildAndroidStaging"
                            ADDRESSABLES_FOLDER = "Android"
                            break
                        case "IOSDevelop":
                            BUILD_TARGET = "iOS"
                            APP_EXT = "ipa"
                            BUILD_NAME = "${GAME_NAME}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.EditorBuild.BuildIOS"
                            ADDRESSABLES_FOLDER = "iOS"
                            break
                        case "IOSRelease":
                            BUILD_TARGET = "iOS"
                            APP_EXT = "app"
                            BUILD_NAME = "${GAME_NAME}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.EditorBuild.BuildIOS"
                            ADDRESSABLES_FOLDER = "iOS"
                            break
                        case "IOSStaging":
                            BUILD_TARGET = "iOS"
                            APP_EXT = "app"
                            BUILD_NAME = "${GAME_NAME}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.EditorBuild.BuildIOS"
                            ADDRESSABLES_FOLDER = "iOS"
                            break
                        case "LinuxDevelop":
                            BUILD_TARGET = "Linux64"
                            APP_EXT = ""
                            BUILD_NAME = "${GAME_NAME}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildDevelop"
                            ADDRESSABLES_FOLDER = "StandaloneLinux64"
                            break
                        case "LinuxRelease":
                            BUILD_TARGET = "Linux64"
                            APP_EXT = ""
                            BUILD_NAME = "${GAME_NAME}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildRelease"
                            ADDRESSABLES_FOLDER = "StandaloneLinux64"
                            break
                        case "LinuxStaging":
                            BUILD_TARGET = "Linux64"
                            APP_EXT = ""
                            BUILD_NAME = "${GAME_NAME}"
                            BUILD_CL = "-executeMethod TinyWizard.Core.Editor.EditorMenus.BuildStaging"
                            ADDRESSABLES_FOLDER = "StandaloneLinux64"
                            break
                    }

					echo "${params.TYPE}"
					echo "${BUILD_CL}"					
					echo "${ADDRESSABLES_FOLDER}"
					echo "${GAME_NAME}"					
                    //echo "Installing Unity"
                    //installEditor()
                    
                    // Windows Modules
                    // unity,visualstudio,visualstudioprofessionalunityworkload,visualstudioenterpriseunityworkload,android,ios,
                    // appletv,linux-il2cpp,linux-mono,linux-server,mac-mono,mac-server,universal-windows-platform,webgl,windows-il2cpp,
                    // windows-server,documentation,language-ja,language-ko,language-zh-hans,language-zh-hant,android-sdk-ndk-tools,
                    // android-sdk-platform-tools,android-sdk-build-tools,android-sdk-platforms-29,android-sdk-platforms-30,android-ndk,
                    // android-sdk-command-line-tools,android-open-jdk.

                    // Mac Modules
                    // unity,mono,visualstudio,android,ios,appletv,linux-il2cpp,linux-mono,linux-server,mac-il2cpp,mac-server,
                    // webgl,windows-mono,windows-server,documentation,language-ja,language-ko,language-zh-hans,language-zh-hant,
                    // android-sdk-ndk-tools,android-sdk-platform-tools,android-sdk-build-tools,android-sdk-platforms-29,
                    // android-sdk-platforms-30,android-ndk,android-sdk-command-line-tools,android-open-jdk

                    // switch(params.TYPE) {
                    //     case "WindowsDevelop":
                    //     case "WindowsRelease":
                    //     case "WindowsStaging":
                    //         installEditorModules("windows-server")
                    //         if(isWindows()) {
                    //             installEditorModules("windows-il2cpp")
                    //         }
                    //         break
                    //     case "AndroidDevelop":
                    //     case "AndroidRelease":
                    //     case "AndroidStaging":
                    //         installEditorModules("android android-sdk-build-tools android-sdk-ndk-tools android-sdk-platforms android-platform-tools android-ndk android-open-jdk")
                    //         break
                    //     case "IOSDevelop":
                    //     case "IOSRelease":
                    //     case "IOSStaging":
                    //         installEditorModules("ios")
                    //         break
                    //     case "LinuxDevelop":
                    //     case "LinuxRelease":
                    //     case "LinuxStaging":
                    //         installEditorModules("linux linux-server")
                    //         if(isLinux()) {
                    //             installEditorModules("linux-il2cpp")
                    //         }
                    //         break
                    // }
                }
            }
        }
        stage('Build') {
            environment {
                UNITY_USERNAME = credentials('UNITY_USERNAME')
                UNITY_PASSWORD = credentials('UNITY_PASSWORD')
                MATCH_PASSWORD = credentials('MATCH_PASSWORD')
                FASTLANE_ISSUER = credentials('FASTLANE_ISSUER')
                FASTLANE_ID = credentials('FASTLANE_ID')
                FASTLANE_PASSWORD = credentials('FASTLANE_PASSWORD')
                keychain_password = credentials('KEYCHAIN_PASSWORD')
                LC_ALL = "en_US.UTF-8"
                LANGUAGE = "en_US.UTF-8"
                LANG = "en_US.UTF-8"
                BuildConfig = "${params.TYPE}"
                PATH = "${getPath()}"
            }
            steps {
                script {
                    echo "Building Unity $BuildConfig"

                    SCRIPTING_BACKEND = ""
                    if(BUILD_SCRIPTING != "Default") {
                        SCRIPTING_BACKEND = "-backend ${BUILD_SCRIPTING} "
                    }

                    def args = "-batchmode -nographics -quit -accept-apiupdate " + 
                            "-username '$UNITY_USERNAME' " + 
                            "-password '$UNITY_PASSWORD' " + 
                            "-logFile - " + 
                            "-projectPath \"$WORKSPACE\" " + 
                            "$SCRIPTING_BACKEND" +
                            "-buildTarget ${BUILD_TARGET} " +
                            "${BUILD_CL} " + 
                            "-buildPath " + updatePath("builds\\${currentBuild.displayName}\\${BUILD_NAME}");

                    if(params.ADDRESSABLES) {
                        args += " -addressables";
                        args += " -addressable-url ${params.ADDRESSABLE_URL}";
                        args += " -addressable-version ${params.ADDRESSABLE_VERSION}";

                        // Make sure we are creating new addressable.
                        // TODO: Support build updates.
                        echo "Clearing existing addressables..."
                        dir ('ServerData') {
                            deleteDir()
                        }
                        dir('Library/com.unity.addressables') {
                            deleteDir()
                        }
                    }

                    if(params.DEEP_PROFILING_SUPPORT) {
                        args += " -deepprofiling";
                    }
                    if(params.WAIT_FOR_DEBUGGER) {
                        args += " -waitfordebugger";
                    }
                    
                    cmd("${getHardCodedInstallPath()} " + args)

                    if(params.TYPE.startsWith("IOS")) {
                        if(!isUnix()) {
                            error("Cannot finish the IOS build in XCode on this platform. Must be on a mac with an up-to-date xcode installed.")
                        } else {
                            cmd "sudo chown -fR jenkins:staff builds/${currentBuild.displayName}/${BUILD_NAME}"
                            cmd "brew list fastlane || brew install fastlane"
                            cmd "fastlane ios build_ipa " +
                                    "bundle_id:com.TinyWizard.TopDownArena " +
                                    "apple_id:1566130057 " +
                                    "repo_url:git@github.com:tinywizardgames/ios-fastlane.git " +
                                    "build_path:builds/${currentBuild.displayName}/${BUILD_NAME} " +
                                    "key_id:$FASTLANE_ID " +
                                    "key_issuer:$FASTLANE_ISSUER " +
                                    "key_content:\"$FASTLANE_PASSWORD\""
                            if(params.POST_CLEANUP) {
                                cmdTry "rm -rf ~/Library/Developer/Xcode/DerivedData"
                                cmdTry "rm -rf ~/Library/Developer/Xcode/Archives"
                            }
                        }
                    } else {
                        if (!fileExists(updatePath("builds\\${currentBuild.displayName}\\${BUILD_NAME}"))) {
                            error('Unity build failed but returned a successful exit code. Scroll up in the logs a bit to find the error message. Try searching for "Error CS" if you\'re having trouble finding it.')
                        }
                    }
                }
            }
        }
        stage('Compressing Artifacts') {
            steps {
                script {
                    switch(params.TYPE) {
                        case "WindowsDevelop":
                        case "WindowsRelease":
                        case "WindowsStaging":
                            if(isUnix()) {
                                cmdTry "sudo rm -rf builds/${currentBuild.displayName}/${GAME_NAME}_BurstDebugInformation_DoNotShip/"
                                cmd "sudo zip -r builds/${currentBuild.displayName}.zip builds/${currentBuild.displayName}/*"
                                if(params.POST_CLEANUP) {
                                    cmdTry "sudo rm -rf builds/${currentBuild.displayName}"
                                }
                            } else {
                                cmdTry "rmdir /S /Q builds\\${currentBuild.displayName}\\${GAME_NAME}_BurstDebugInformation_DoNotShip"
                                cmd "7z a builds\\${currentBuild.displayName}.zip .\\builds\\${currentBuild.displayName}\\*"
                                if(params.POST_CLEANUP) {
                                    cmdTry "rmdir /S /Q builds\\${currentBuild.displayName}"
                                }
                            }
                            break
                        case "AndroidDevelop":
                        case "AndroidRelease":
                        case "AndroidStaging":
                            if(isUnix()) {
                                cmd "sudo mv -f builds/${currentBuild.displayName}/${BUILD_NAME} builds/${currentBuild.displayName}.${APP_EXT}"
                                if(params.POST_CLEANUP) {
                                    cmdTry "sudo rm -rf builds/${currentBuild.displayName}"
                                }
                            } else {
                                cmd "move /Y builds\\${currentBuild.displayName}\\${BUILD_NAME} builds\\${currentBuild.displayName}.${APP_EXT}"
                                if(params.POST_CLEANUP) {
                                    cmdTry "rmdir /S /Q builds\\${currentBuild.displayName}"
                                }
                            }
                            break
                        case "IOSDevelop":
                        case "IOSRelease":
                        case "IOSStaging":
                            cmd "sudo cp -f builds/${currentBuild.displayName}/${BUILD_NAME}/Output/Output.${APP_EXT} builds/${currentBuild.displayName}.${APP_EXT}"
                            if(!params.DEPLOY && params.POST_CLEANUP) {
                                cmd "sudo rm -rf builds/${currentBuild.displayName}"
                            }
                            break
                        case "LinuxDevelop":
                        case "LinuxRelease":
                        case "LinuxStaging":
                            break
                    }
                }
            }
        }
        stage('Uploading Artifacts') {
            steps {
                script {
                    switch(params.TYPE) {
                        case "WindowsDevelop":
                        case "WindowsRelease":
                        case "WindowsStaging":
                            ArchiveWindows();
                            BUILD_COMPLETE = true
                            break
                        case "AndroidDevelop":
                        case "AndroidRelease":
                        case "AndroidStaging":
                            ArchiveAndroid();
                            BUILD_COMPLETE = true
                            break
                        case "IOSDevelop":
                        case "IOSRelease":
                        case "IOSStaging":
                            ArchiveIOS();
                            BUILD_COMPLETE = true
                            break
                        case "LinuxDevelop":
                        case "LinuxRelease":
                        case "LinuxStaging":
                            ArchiveLinux();
                            BUILD_COMPLETE = true
                            break
                    }
                }
            }
        }
        stage('Deploy') {
            environment {
                TESTFLIGHT_USERNAME = credentials('TESTFLIGHT_USERNAME')
                TESTFLIGHT_PASSWORD = credentials('TESTFLIGHT_PASSWORD')
                FASTLANE_ISSUER = credentials('FASTLANE_ISSUER')
                FASTLANE_ID = credentials('FASTLANE_ID')
                FASTLANE_PASSWORD = credentials('FASTLANE_PASSWORD')
                LC_ALL = "en_US.UTF-8"
                LANGUAGE = "en_US.UTF-8"
                LANG = "en_US.UTF-8"
                PATH = "${getPath()}"
            }
            when {
                expression { return params.DEPLOY }
            }
            steps {
                script {
                    switch(params.TYPE) {
                        case "WindowsDevelop":
                        case "WindowsRelease":
                        case "WindowsStaging":
                            break
                        case "AndroidDevelop":
                        case "AndroidRelease":
                        case "AndroidStaging":
                            break
                        case "IOSDevelop":
                        case "IOSRelease":
                        case "IOSStaging":
                            cmd "fastlane pilot upload " +
                                    "--ipa builds/${currentBuild.displayName}.${APP_EXT} " +
                                    "--api_key \"{\\\"key_id\\\":\\\"$FASTLANE_ID\\\",\\\"issuer_id\\\":\\\"$FASTLANE_ISSUER\\\",\\\"key\\\":\\\"$FASTLANE_PASSWORD\\\"}\""

                            if(params.POST_CLEANUP) {
                                cmdTry "sudo rm -rf builds/${currentBuild.displayName}"
                                cmdTry "sudo rm builds/${currentBuild.displayName}.${APP_EXT}"
                            }
                            if(params.NOTIFY) {
                                discordSend description: "${params.SUBTARGET} build started by *${BUILD_USER}* has been deployed. (${buildTime})\n" +
                                        "**Build Name**: ${currentBuild.displayName}\n" +
                                        "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                                        "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                                        "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                                        link: env.BUILD_URL, 
                                        result: currentBuild.currentResult, 
                                        title: "[${BUILD_DISPLAY_NUMBER}] Build Deployed ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                        webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                            }
                            break
                        case "LinuxDevelop":
                        case "LinuxRelease":
                        case "LinuxStaging":
                            break
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                // def jenkins = Jenkins.getinstance()
                // def job = jenkins.getItemByFullName(env.JOB_NAME)
                // def build = job.getBuildByNumber(Integer.parseInt(env.BUILD_NUMBER))
                // def log = build.logFile.text

                if(params.POST_CLEANUP) {
                    if(isUnix()) {
                        cmdTry "sudo rm -rf builds/${currentBuild.displayName}"
                        cmdTry "sudo rm builds/${currentBuild.displayName}.${APP_EXT}"
                        cmdTry "sudo rm builds/${currentBuild.displayName}.zip"
                        if(isMac()) {
                            cmdTry "rm -rf ~/Library/Developer/Xcode/DerivedData"
                            cmdTry "rm -rf ~/Library/Developer/Xcode/Archives"
                        }
                    } else {
                        cmdTry "rmdir /S /Q builds\\${currentBuild.displayName}"
                        cmdTry "del /Q builds\\${currentBuild.displayName}.zip"
                    }
                }
                if(params.NOTIFY) {
                    def buildTime = currentBuild.durationString.replace(" and counting", "")
                    if(params.DEPLOY && BUILD_COMPLETE) {
                        discordSend description: "Build started by *${BUILD_USER}* failed to deploy. (${buildTime})\n" +
                                "**Build Name**: ${currentBuild.displayName}\n" +
                                "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                                "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                                "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                                result: currentBuild.currentResult, 
                                title: "[${BUILD_DISPLAY_NUMBER}] Build Deploy Failed ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                    } else {
                        discordSend description: "Build started by *${BUILD_USER}* failed. (${buildTime})\n" +
                                "**Build Name**: ${currentBuild.displayName}\n" +
                                "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                                "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                                "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                                result: currentBuild.currentResult, 
                                title: "[${BUILD_DISPLAY_NUMBER}] Build Failed ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                    }
                }
            }
        }
        aborted {
            script {
                if(isUnix()) {
                    if(params.POST_CLEANUP) {
                        cmdTry "sudo rm -rf builds/${currentBuild.displayName}"
                    }
                    
                    cmdTry "sudo pkill Unity"
                    cmdTry "sudo pkill il2cpp"
                    cmdTry "sudo pkill dotnet"
                    if(isMac()) {
                        if(params.POST_CLEANUP) {
                            cmdTry "rm -rf ~/Library/Developer/Xcode/DerivedData"
                            cmdTry "rm -rf ~/Library/Developer/Xcode/Archives"
                        }
                        cmdTry "sudo pkill xcodebuild"
                    }
                } else {
                    if(params.POST_CLEANUP) {
                        cmdTry "rmdir /S /Q builds\\${currentBuild.displayName}"
                    }
                    cmdTry "taskkill Unity.exe"
                }


                if(params.NOTIFY) {
                    def buildTime = currentBuild.durationString.replace(" and counting", "")
                    if(params.DEPLOY && BUILD_COMPLETE) {
                        discordSend description: "Build started by *${BUILD_USER}* completed but the deploy was aborted. (${buildTime})\n" +
                                "**Build Name**: ${currentBuild.displayName}\n" +
                                "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                                "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                                "**Log**:\n[${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                                link: env.BUILD_URL, 
                                result: currentBuild.currentResult, 
                                title: "[${BUILD_DISPLAY_NUMBER}] Deploy Aborted ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                    } else {
                        discordSend description: "Build started by *${BUILD_USER}* was aborted. (${buildTime})\n" +
                                "**Build Name**: ${currentBuild.displayName}\n" +
                                "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                                "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                                "**Log**:\n[${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                                link: env.BUILD_URL, 
                                result: currentBuild.currentResult, 
                                title: "[${BUILD_DISPLAY_NUMBER}] Build Aborted ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                                webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
                    }
                }
            }
        }
    }
}

void cmd(String code) {
    if (isUnix()) {
        sh(code)
    } else {
        bat(code)
    }
}
String cmdStd(String code) {
    if (isUnix()) {
        return sh(script: code, returnStdout: true)
    } else {
        return bat(script: code, returnStdout: true)
    }
}
Boolean cmdTry(String code) {
    try {
        if (isUnix()) {
            sh(code)
        } else {
            bat(code)
        }
        return true;
    } catch (err) {
        echo err.getMessage()
        return false;
    }
}
String updatePath(String filePath) {
    if (isUnix()) {
        return filePath.replace("\\", "/")
    } else {
        return filePath.replace("/", "\\")
    }
}
Boolean isLinux() {
    if (!isUnix()) {
        return false
    }
    def uname = sh script: 'uname', returnStdout: true
    if (uname.startsWith("Darwin")) {
        return false
    }
    return true
}
Boolean isMac() {
    if (!isUnix()) {
        return false
    }
    def uname = sh script: 'uname', returnStdout: true
    if (uname.startsWith("Darwin")) {
        return true
    }
    return false
}
Boolean isWindows() {
    if (!isUnix()) {
        return true
    }
    return false
}
String getPath() {
    if(isLinux()) {
        return "" // TODO
    } else if(isMac()) {
        return "/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/share/dotnet:~/.dotnet/tools:/Library/Apple/usr/bin:/Library/Frameworks/Mono.framework/Versions/Current/Commands"
    } else {
        return "" // TODO
    }
}
void installEditor() {
    // if(isLinux()) {
    //     cmd("${UNITY_INSTALL_LIN}" + " install -v ${EDITOR_VERSION}")
    // } else if(isMac()) {
    //     cmd("${UNITY_INSTALL_MAC}" + " install -v ${EDITOR_VERSION}")
    // } else {
    //     cmd("${UNITY_INSTALL_WIN}" + " install -v ${EDITOR_VERSION}")
    // }
}
void installEditorModules(String modules) {
    // if(isLinux()) {
    //     cmd("${UNITY_INSTALL_LIN}" + " install-modules -v ${EDITOR_VERSION} -m ${modules}")
    // } else if(isMac()) {
    //     cmd("${UNITY_INSTALL_MAC}" + " install-modules -v ${EDITOR_VERSION} -m ${modules}")
    // } else {
    //     cmd("${UNITY_INSTALL_WIN}" + " install-modules -v ${EDITOR_VERSION} -m ${modules}")
    // }
}
String getHardCodedInstallPath() {
    if(isLinux()) {
        // TODO: Determine install location.
        return ""
    } else if(isMac()) {
        return "sudo /Applications/Unity/Hub/Editor/${EDITOR_VERSION}/Unity.app/Contents/MacOS/Unity"
    } else {
        //return "C:\\Program Files\\Unity\\Hub\\Editor\\${EDITOR_VERSION}\\Unity.exe"
        return "D:\\Tools\\Unity\\${EDITOR_VERSION}\\Unity.exe"
    }
}


void ArchiveWindows() {
    def buildTime = currentBuild.durationString.replace(" and counting", "")
    withAWS(region:'eu-west-2', credentials:'868aa55f-6aa4-4962-95d5-e00a7b95e1e1') {
        archiveArtifacts artifacts: updatePath("builds\\${currentBuild.displayName}.zip"), onlyIfSuccessful: true

        if(params.ADDRESSABLES) {
            ArchiveAddressables();
        }
    }

    // Delete the zip file because it has been archived and moved to Jenkins and because when we deploy to steam we dont want to include the zip
    if(isUnix()) {
        if(params.POST_CLEANUP) {
            cmdTry "sudo rm -f builds/${currentBuild.displayName}.zip"
        }
    } else {
        if(params.POST_CLEANUP) {
            cmdTry "del /Q builds\\${currentBuild.displayName}.zip"
        }
    }
    if(params.NOTIFY) {
        discordSend description: "${params.SUBTARGET} build started by *${BUILD_USER}* is complete. (${buildTime})\n" +
                "**Build Name**: ${currentBuild.displayName}\n" +
                "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                "**Download**: [${currentBuild.displayName}.zip](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/artifact/builds/${currentBuild.displayName}.zip)\n" +
                "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                link: env.BUILD_URL, 
                result: currentBuild.currentResult, 
                title: "[${BUILD_DISPLAY_NUMBER}] Build Complete ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
    }
}
void ArchiveAndroid() {
    def buildTime = currentBuild.durationString.replace(" and counting", "")
    withAWS(region:'eu-west-2', credentials:'868aa55f-6aa4-4962-95d5-e00a7b95e1e1') {
        archiveArtifacts artifacts: updatePath("builds\\${currentBuild.displayName}.${APP_EXT}"), onlyIfSuccessful: true

        if(params.ADDRESSABLES) {
            ArchiveAddressables();
        }
    }

    if(isUnix()) {
        if(params.POST_CLEANUP) {
            cmdTry "sudo rm builds/${currentBuild.displayName}.${APP_EXT}"
        }
    } else {
        if(params.POST_CLEANUP) {
            cmdTry "del /Q builds\\${currentBuild.displayName}.${APP_EXT}"
        }
    }
    if(params.NOTIFY) {
        discordSend description: "${params.SUBTARGET} build started by *${BUILD_USER}* is complete. (${buildTime})\n" +
                "**Build Name**: ${currentBuild.displayName}\n" +
                "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                "**Download**: [${currentBuild.displayName}.${APP_EXT}](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/artifact/builds/${currentBuild.displayName}.${APP_EXT})\n" +
                "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)", 
                link: env.BUILD_URL, 
                result: currentBuild.currentResult, 
                title: "[${BUILD_DISPLAY_NUMBER}] Build Complete ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
    }
}
void ArchiveIOS() {
    def buildTime = currentBuild.durationString.replace(" and counting", "")

    def install_url;
    withAWS(region:'eu-west-2', credentials:'868aa55f-6aa4-4962-95d5-e00a7b95e1e1') {
        archiveArtifacts artifacts: updatePath("builds\\${currentBuild.displayName}.${APP_EXT}"), onlyIfSuccessful: true
        def download_url = s3PresignURL(bucket: "twbr-game-server-builds-euw2", key: "JumpPlayground/${BUILD_NUMBER}/artifacts/builds/${currentBuild.displayName}.${APP_EXT}", durationInSeconds: 604800);
        echo download_url

        def manifestContent = readFile(file: updatePath("Jenkins\\manifest.plist"))
        manifestContent = manifestContent.replace("%DOWNLOAD_URL%", "${download_url}")
        echo manifestContent
        writeFile(file: updatePath("Jenkins\\manifest.plist"), text: manifestContent)

        archiveArtifacts artifacts: updatePath("Jenkins\\manifest.plist"), onlyIfSuccessful: true
        s3Upload(pathStyleAccessEnabled: true, 
                        payloadSigningEnabled: true, 
                        file: updatePath("Jenkins\\manifest.plist"), 
                        bucket:"twbr-game-server-builds-euw2", 
                        path: "JumpPlayground/${BUILD_NUMBER}/artifacts/Jenkins/manifest.plist",
                        tags: '[public:yes]')
        def manifest_url = "https://twbr-game-server-builds-euw2.s3.eu-west-2.amazonaws.com/JumpPlayground/${BUILD_NUMBER}/artifacts/Jenkins/manifest.plist"
        echo manifest_url

        def htmlContent = readFile(file: updatePath("Jenkins\\install_ios.html"))
        htmlContent = htmlContent.replace("%MANIFEST_URL%", "${manifest_url}")
        echo htmlContent
        writeFile(file: updatePath("Jenkins\\install_ios.html"), text: htmlContent)

        archiveArtifacts artifacts: updatePath("Jenkins\\install_ios.html"), onlyIfSuccessful: true
        s3Upload(pathStyleAccessEnabled: true, 
                        payloadSigningEnabled: true, 
                        file: updatePath("Jenkins\\install_ios.html"), 
                        bucket:"twbr-game-server-builds-euw2", 
                        path: "JumpPlayground/${BUILD_NUMBER}/artifacts/Jenkins/install_ios.html",
                        tags: '[public:yes]')
        install_url = "https://twbr-game-server-builds-euw2.s3.eu-west-2.amazonaws.com/JumpPlayground/${BUILD_NUMBER}/artifacts/Jenkins/install_ios.html"
        echo install_url

        if(params.ADDRESSABLES) {
            ArchiveAddressables();
        }
    }

    if(!params.DEPLOY && params.POST_CLEANUP) {
        //cmdTry "sudo rm builds/${currentBuild.displayName}.${APP_EXT}"
    }
    if(params.NOTIFY) {
        if(params.DEPLOY) {
            discordSend description: "${params.SUBTARGET} build started by *${BUILD_USER}* is complete. (${buildTime})\n" +
                    "**Build Name**: ${currentBuild.displayName}\n" +
                    "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                    "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)\n" +
                    "**Download**: [${currentBuild.displayName}.${APP_EXT}](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/artifact/builds/${currentBuild.displayName}.${APP_EXT})\n" + 
                    "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                    "**Install**: [${currentBuild.displayName}.${APP_EXT}](${install_url})\n" + 
                    "Deploy in progress...", 
                    link: env.BUILD_URL, 
                    result: currentBuild.currentResult, 
                    title: "[${BUILD_DISPLAY_NUMBER}] Build Complete ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                    webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
        } else {
            discordSend description: "${params.SUBTARGET} build started by *${BUILD_USER}* is complete. (${buildTime})\n" + 
                    "**Build Name**: ${currentBuild.displayName}\n" +
                    "**Branch**: ${params.BRANCH} ($GIT_COMMIT)\n" +
                    "**Log**: [${JOB_NAME}/${BUILD_NUMBER}/console](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/console)\n" +
                    "**Download**: [${currentBuild.displayName}.${APP_EXT}](${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/artifact/builds/${currentBuild.displayName}.${APP_EXT})\n" + 
                    "**Platform**: ${params.TYPE} ${params.SUBTARGET}\n" +
                    "**Install**: [${currentBuild.displayName}.${APP_EXT}](${install_url})", 
                    link: env.BUILD_URL, 
                    result: currentBuild.currentResult, 
                    title: "[${BUILD_DISPLAY_NUMBER}] Build Complete ${params.TYPE} ${params.SUBTARGET} (${params.BRANCH})", 
                    webhookURL: "https://discord.com/api/webhooks/1192274485760491570/v87opeAJTsjLjq2btDTlf8W5K-7Q948cZ3ful4Io6OG5x3Ic3D8ZgCltIo_334c1zNWh"
        }
    }
}
void ArchiveLinux() {
    def buildTime = currentBuild.durationString.replace(" and counting", "")
}
void ArchiveAddressables() {
    dir("ServerData/" + ADDRESSABLES_FOLDER) {
        s3Delete(bucket: "twbr-addressables", path: "TestTristan/${ADDRESSABLES_FOLDER}/");
        def files = findFiles(glob: "*");
        files.each() {
            echo "Uploading addressable " + it.path + " with AWS credentials..."
            echo "${params.ADDRESSABLE_VERSION}/${ADDRESSABLES_FOLDER}/${it.path}";
            s3Upload(pathStyleAccessEnabled: true, 
                    payloadSigningEnabled: true, 
                    file: "${it.path}", 
                    bucket:"twbr-addressables", 
                    path: "${params.ADDRESSABLE_VERSION}/${ADDRESSABLES_FOLDER}/${it.path}",
                    tags: '[public:yes]')
            echo 'Upload complete.'
        }
    }
}