def call(String projectid) {
    sh "echo libarycalled"
    sh "echo GCloudproject- ${GCLOUD_PROJECT}"
}
