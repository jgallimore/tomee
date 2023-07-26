#! /bin/sh
#This script automatically:
# - increase TomEE version in the project pom.xml files
# - Stage and commit to local repository the change.

findTomEEVersion(){
  xmllint --xpath '/*[local-name()="project"]/*[local-name()="properties"]/*[local-name()="tomee.version"]/text()' pom.xml
}

TOMEE_VERSION=$(findTomEEVersion)

NEXT_TOMEE_VERSION=$(awk -vFS=. -vOFS=. '{$NF++;print}' <<<${TOMEE_VERSION})


echo "\nFrom Current TomEE version:" $TOMEE_VERSION "poms will be updated to: " $NEXT_TOMEE_VERSION
echo "\nGIT STATUS SHOULDNT SHOW ANY STAGING CHANGE AT THIS POINT:"
git status
echo ""


read -p "Do you want to proceed? (yes/no) " yn

case $yn in
	yes|y )
    echo Updating TomEE version in poms...
	  find . -type f -name pom.xml -exec sed -i '' "s/${TOMEE_VERSION}<\//${NEXT_TOMEE_VERSION}<\//g" {} \;
	  #Changes the version for pom-template.xml. Execute this after boms has been updated
	  find . -type f -name pom-template.xml -exec sed -i '' "s/${TOMEE_VERSION}<\//${NEXT_TOMEE_VERSION}<\//g" {} \;
    echo Stagging changes to local git repo...
    git add .
    echo Committing changes to local git repo...
    git commit -m "Prepare for release ${NEXT_TOMEE_VERSION}"
    echo Operation succed. Now you just need to execut GIT PUSH.
    echo *** DONT FORGET TO RUN GenerateBoms.java FROM IDE ***
	;;
	no|n ) echo Operation has been cancel by user.;
		exit;;
	* ) echo invalid response;
		exit 1;;
esac

