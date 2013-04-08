# Jenkins RHNPush Plugin

[![Build Status](https://buildhive.cloudbees.com/job/specems/job/jenkins-rhnpush-plugin/badge/icon)](https://buildhive.cloudbees.com/job/specems/job/jenkins-rhnpush-plugin/)

This plugin adds a post-build step to push RPMs to Spacewalk or RHN satellite server.

## Usage

Configure some satellite servers in the global config:

![Add satellite server](http://specems.github.io/jenkins-rhnpush-plugin/images/satellite-server-config.png)

Then add a post-build action:

![Add post-build action](http://specems.github.io/jenkins-rhnpush-plugin/images/add-post-build-action.png)

And finally select the RPM(s) to push to which channels:

![Add RPMs to push](http://specems.github.io/jenkins-rhnpush-plugin/images/job_config.png)
