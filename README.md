# RIBsKotlinPlugin
Uber RIBs Kotlin code generator for Android Studio

Original Java code generator from Uber can be viewed on this link.

https://github.com/uber/RIBs/tree/master/android/tooling

# Screenshots

![Alt text](/screenshots/screen_1a.png?raw=true "Screenshot")

![Alt text](/screenshots/screen_2a.png?raw=true "Screenshot")

# Development

1. Clone this repo
2. Download, install, and run [IDEA CE](https://www.jetbrains.com/idea/download/)
3. File -> Open...
4. Select your local repo folder
5. Make appropriate changes to `resources/template/kotlin`
6. Build -> Prepare Plugin Module 'rib-intellij-plugin' For Deployment
7. You now have a JAR in your repo folder you can test with (see below)
8. Move the JAR to `deploy` folder once you're satisfied before sending the PR

# Testing

1. Generate the JAR using above steps
2. Open Android Studio
3. Go to Preferences (⌘ + ,) -> Plugins
4. Click Install plugin from disk...
5. Select your JAR
6. Restart Android studio via convenient button inside Preferences
7. In a source package, create a New -> New RIB...
8. Build the code to ensure no compile errors