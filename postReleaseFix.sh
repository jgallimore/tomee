#! /bin/bash
#
#   Licensed to the Apache Software Foundation (ASF) under one or more
#   contributor license agreements.  See the NOTICE file distributed with
#   this work for additional information regarding copyright ownership.
#   The ASF licenses this file to You under the Apache License, Version 2.0
#   (the "License"); you may not use this file except in compliance with
#   the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
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

    if [[ "$OSTYPE" == "darwin"* ]]; then
            echo "\n Darwing OS detected, updating old TomEE version in poms..."
            find . -type f -name pom.xml -exec sed -i '' "s/${OLD_TOMEE_VERSION}<\//${TOMEE_VERSION}<\//g" {} \;
            find . -type f -name arquillian.xml -exec sed -i '' "s/mvn:org.superbiz:properties-provider-impl:${OLD_TOMEE_VERSION}/mvn:org.superbiz:properties-provider-impl:${TOMEE_VERSION}/g" {} \;
    else
            echo "\n Unix OS detected, updating old TomEE version in poms..."
            find . -type f -name pom.xml -exec sed -i "s/${OLD_TOMEE_VERSION}<\//${TOMEE_VERSION}<\//g" {} \;
            find . -type f -name arquillian.xml -exec sed -i "s/mvn:org.superbiz:properties-provider-impl:${OLD_TOMEE_VERSION}/mvn:org.superbiz:properties-provider-impl:${TOMEE_VERSION}/g" {} \;
    fi

   	git status
    echo Stagging changes to local git repo...
    git add .
    echo Committing changes to local git repo...
    git commit -m "Post release auto fix"
    echo Operation succed. Now you just need to execut GIT PUSH.
    exit 0
fi




