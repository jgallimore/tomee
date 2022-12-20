#! /bin/sh
#This script automatically:
# - increase both Open EJB and TomEE version in the project pom.xml files
# - Stage and commit to local repository the change.

findOpenEjbVersion() {
    xmllint --xpath '/*[local-name()="project"]/*[local-name()="properties"]/*[local-name()="openejb.version"]/text()' pom.xml
}

findTomEEVersion(){
  xmllint --xpath '/*[local-name()="project"]/*[local-name()="properties"]/*[local-name()="tomee.version"]/text()' pom.xml
}

OPEN_EJB_VERSION=$(findOpenEjbVersion)
TOMEE_VERSION=$(findTomEEVersion)

NEXT_EJB_VERSION=$(awk -vFS=. -vOFS=. '{$NF++;print}' <<<${OPEN_EJB_VERSION})
NEXT_TOMEE_VERSION=$(awk -vFS=. -vOFS=. '{$NF++;print}' <<<${TOMEE_VERSION})


echo "\nFrom current Open EJB version:" $OPEN_EJB_VERSION "poms will be updated to: " $NEXT_EJB_VERSION
echo "From Current TomEE version:" $TOMEE_VERSION "poms will be updated to: " $NEXT_TOMEE_VERSION
echo "\nGIT STATUS SHOULDNT SHOW ANY STAGING CHANGE AT THIS POINT:"
git status
echo ""


read -p "Do you want to proceed? (yes/no) " yn

case $yn in
	yes|y )
	  echo Updating Open EJB version in poms...
	  find . -type f -name pom.xml -exec sed -i '' "s/${OPEN_EJB_VERSION}<\//${NEXT_EJB_VERSION}<\//g" {} \;
    echo Updating TomEE version in poms...
	  find . -type f -name pom.xml -exec sed -i '' "s/${TOMEE_VERSION}<\//${NEXT_TOMEE_VERSION}<\//g" {} \;
    echo Stagging changes to local git repo...
    git add .
    echo Committing changes to local git repo...
    git commit -m "Prepare for release ${NEXT_TOMEE_VERSION}"
    echo Operation succed. Now you just need to execut GIT PUSH.
	;;
	no|n ) echo Operation has been cancel by user.;
		exit;;
	* ) echo invalid response;
		exit 1;;
esac

