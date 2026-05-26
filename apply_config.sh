ld.gradle
echo "正在配置根目录 build.gradle..."
cat > build.gradle <<EOF
plugins {
    id 'com.android.application' version '8.2.2' apply false
}
EOF

# 2. 修复 settings.gradle
# echo "正在配置 settings.gradle..."
# echo "rootProject.name = 'GameMonitor'" > settings.gradle
# echo "include ':app'" >> settings.gradle
#
# # 3. 修复 app/build.gradle
# echo "正在配置 app/build.gradle..."
# cat > app/build.gradle <<EOF
# plugins {
#     id 'com.android.application'
#     }
#
#     android {
#         namespace 'com.example.yoloscreen'
#             compileSdk 34
#
#                 defaultConfig {
#                         applicationId "com.example.yoloscreen"
#                                 minSdk 24
#                                         targetSdk 34
#                                             }
#                                                 
#                                                     sourceSets {
#                                                             main {
#                                                                         manifest.srcFile 'src/main/AndroidManifest.xml'
#                                                                                     java.srcDirs = ['src/main/java']
#                                                                                                 jniLibs.srcDirs = ['src/main/jniLibs']
#                                                                                                             assets.srcDirs = ['src/main/assets']
#                                                                                                                     }
#                                                                                                                         }
#                                                                                                                         }
#                                                                                                                         EOF
#
#                                                                                                                         echo "配置已应用！"
#                                                                                                                         
