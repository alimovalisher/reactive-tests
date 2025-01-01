#!/bin/bash

export PGPASSWORD='postgres'
DURATION="10m"
ADDRESS=http://localhost:8080
SLEEP='1m'
TEST_TYPE="web"

for THREADS in 4 8 16 64
do
  for MESSAGES in 1 10 100 1000
  do
    echo "run test message add THREADS: ${THREADS} MESSAGES: ${MESSAGES}"

    java -jar build/libs/service-tests-1.0.0-SNAPSHOT.jar messages:create --type ${TEST_TYPE} --users 1000 --threads ${THREADS} --messages ${MESSAGES} --duration ${DURATION} --address ${ADDRESS}

    psql -U postgres -h localhost -c 'truncate message'
    echo "Sleep ${SLEEP}..."
    sleep ${SLEEP}
  done
done


# clean data
echo "clean table before next tests"
psql -U postgres -h localhost -c 'truncate message'
echo "Sleep ${SLEEP}..."
sleep ${SLEEP}



for THREADS in 4 8 16 64
do
  for MESSAGES in 1 10 100 1000
    do

      # generate messages
      echo "generate messages ${MESSAGES}"

      java -jar build/libs/service-tests-1.0.0-SNAPSHOT.jar messages:bootstrap --type ${TEST_TYPE} --users 1000 --messages ${MESSAGES} --address ${ADDRESS}
      echo "Sleep ${SLEEP}..."
      sleep ${SLEEP}

      ## run tests

      echo "run test message get THREADS: ${THREADS} MESSAGES: ${MESSAGES}"

      java -jar build/libs/service-tests-1.0.0-SNAPSHOT.jar messages:get --type ${TEST_TYPE} --users 1000 --threads ${THREADS} --duration ${DURATION} --address ${ADDRESS}

      # clean data
      psql -U postgres -h localhost -c 'truncate message'
      echo "Sleep ${SLEEP}..."
      sleep ${SLEEP}
    done

done

