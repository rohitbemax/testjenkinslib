def call(String gkeSAKeyFile, String gcloudProject, String topicId, String chartVersion, String dockerImageTag, int timeout) {
    sh """
        export VERSION=\$(./gradlew properties -q |  grep 'version:' | awk '{print \$2}')
        CORRELATION_ID=\$(dbus-uuidgen)
        numOfIterations=0

        gcloud auth activate-service-account --key-file $gkeSAKeyFile
        gcloud config set project "$gcloudProject"

        gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins || true
        gcloud pubsub subscriptions create one-platform-dev-solution-worker-jenkins --topic projects/lucidworks-dev/topics/one-platform-dev --message-filter=\"attributes.correlation_id=\\"\$CORRELATION_ID\\" AND attributes.event_type != \\"CONTINUOUS_DEPLOYMENT_REQUEST\\"\"  || exit 1
        gcloud pubsub topics publish $topicId --attribute event_type=CONTINUOUS_DEPLOYMENT_REQUEST,correlation_id=\$CORRELATION_ID --message="{\"component_name\": \"one-platform-solution-worker\",\"helm_chart_name\": \"one-platform-solution-worker\",\"helm_chart_version\": \"$chartVersion\", \"docker_image_tag\": \"\$VERSION-$dockerImageTag\" }"

        counter=1
        numOfIterations=`expr $timeout / 10`
        while [ \$counter -le \$numOfIterations ]
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
                                    
            if [ \$counter -eq \$numOfIterations ]
            then
                echo \"Timed out waiting for results\"
                gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins
                exit 1
            fi
                                    
            counter=`expr \$counter + 1`
            sleep 10
        done
                                
        gcloud pubsub subscriptions delete one-platform-dev-solution-worker-jenkins   
    """
}
