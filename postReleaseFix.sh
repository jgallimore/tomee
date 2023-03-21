#! /bin/sh
#This script help to fix the post release pom files that didn't were updated by the maven-release-plugin

findTomEEVersion(){
  xmllint --xpath '/*[local-name()="project"]/*[local-name()="version"]/text()' pom.xml
}

findOldTomEEVersion(){
#  This is a common path where the version is not updated during maven release via maven-release-plugin
   xmllint --xpath '/*[local-name()="project"]/*[local-name()="version"]/text()' examples/mbean-auto-registration/pom.xml
}

echo "Detecting post release pom.xml inconsistencies..."

TOMEE_VERSION=$(findTomEEVersion)
OLD_TOMEE_VERSION=$(findOldTomEEVersion)


if [ "$TOMEE_VERSION" = "$OLD_TOMEE_VERSION" ]; then
    echo "\n There are not inconsistencies for the curren TomEE version: " $TOMEE_VERSION
    exit 0
else
    echo "\n Old TomEE version was found :" $OLD_TOMEE_VERSION " and poms will be updated to current project version: " $TOMEE_VERSION
    echo "\n GIT STATUS SHOULD NOT SHOW ANY STAGING CHANGE AT THIS POINT:"
    git status
    echo "\n Updating old TomEE version in poms..."
   	find . -type f -name pom.xml -exec sed -i '' "s/${OLD_TOMEE_VERSION}<\//${TOMEE_VERSION}<\//g" {} \;
   	git status
    echo Stagging changes to local git repo...
    git add .
    echo Committing changes to local git repo...
    git commit -m "Post release auto fix"
    echo Operation succed. Now you just need to execut GIT PUSH.
    exit 0
fi




