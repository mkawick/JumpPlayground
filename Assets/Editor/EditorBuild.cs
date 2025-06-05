using System;
using TinyWizard.Utilities;
using UnityEditor;

namespace TinyWizard.Core
{
    public static class EditorBuild
    {
        public static void BuildIOS()
        {
            var args = new Args(Environment.GetCommandLineArgs());

            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions()
            {
                locationPathName = args.Get("buildPath"),
                options = BuildOptions.Development,
                scenes = new string[] { "Assets/Scenes/CharacterPlaygroundBlockout.unity" },
                target = args.Get<BuildTarget>("buildTarget"),
            });

           // Environment.Exit(report.summary.result == UnityEditor.Build.Reporting.BuildResult.Succeeded ? 0 : 1);
        }

        public static void BuildAndroid()
        {
            var args = new Args(Environment.GetCommandLineArgs());

            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions()
            {
                locationPathName = args.Get("buildPath"),
                options = BuildOptions.Development,
                scenes = new string[] { "Assets/Scenes/CharacterPlaygroundBlockout.unity" },
                target = args.Get<BuildTarget>("buildTarget"),
            });

            // Environment.Exit(report.summary.result == UnityEditor.Build.Reporting.BuildResult.Succeeded ? 0 : 1);
        }
    }
}
