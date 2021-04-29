def call(String projectid) {
    //sh "echo libarycalled"
    //sh "echo GCloudproject- ${GCLOUD_PROJECT}"
    sh """
        export VERSION=\$(./gradlew properties -q |  grep 'version:' | awk '{print \$2}')
        CORRELATION_ID=\$(dbus-uuidgen)

        gcloud auth activate-service-account --key-file ${GKE_SA_KEYFILE}
        gcloud config set project "${GCLOUD_PROJECT}"

        gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins || true
        gcloud pubsub subscriptions create one-platform-dev-solution-worker-jenkins --topic projects/lucidworks-dev/topics/one-platform-dev --message-filter=\"attributes.correlation_id=\\"\$CORRELATION_ID\\" AND attributes.event_type != \\"CONTINUOUS_DEPLOYMENT_REQUEST\\"\"  || exit 1
        gcloud pubsub topics publish ${TOPIC_ID} --attribute event_type=CONTINUOUS_DEPLOYMENT_REQUEST,correlation_id=\$CORRELATION_ID --message="{\"component_name\": \"one-platform-solution-worker\",\"helm_chart_name\": \"one-platform-solution-worker\",\"helm_chart_version\": \"${CHART_VERSION}\", \"docker_image_tag\": \"\$VERSION-${env.GIT_SHORT_HASH}\" }"

        a=1
        m=12
        while [ \$a -le \$m ]
        do
            CI_RESULT=\$(gcloud pubsub subscriptions pull projects/lucidworks-dev/subscriptions/one-platform-dev-solution-worker-jenkins --auto-ack --format \"value(message.attributes.event_type)\")
            if [ \"\$CI_RESULT\" = \"CONTINUOUS_DEPLOYMENT_SUCCESS\" ]
            then
                echo \"Deployment successful\"
                break;
            elif [ "\$CI_RESULT" != \"\" ]
            then
                echo \"Deployment Failure: \$CI_RESULT\"
                gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins
                exit 1
            fi
                                    
            if [ \$a -eq \$m ]
            then
                echo \"Timed out waiting for results\"
                gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins
                exit 1
            fi
                                    
            a=`expr \$a + 1`
            sleep 10
        done
                                
        gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins   
    """
}
