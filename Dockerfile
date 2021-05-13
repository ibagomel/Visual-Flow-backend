#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
FROM openjdk:11

ARG uid=1001
ARG gid=1002
ARG group=vf-group
ARG username=vf-user

RUN apt-get -y update \
    && apt-get -y upgrade \
    && apt-get -y autoremove

RUN groupadd -g ${gid} ${group} \
    && useradd -u ${uid} -g ${gid} -m ${username}

RUN chown -R ${username}:${group} "$JAVA_HOME" \
    && chmod 644 "$JAVA_HOME/lib/security/cacerts"

USER ${username}
WORKDIR /home/${username}

COPY ./target/vf-api.jar ./
COPY generate_keystore_p12.sh ./

CMD ["/bin/sh", "-c", "sh ./generate_keystore_p12.sh; java -Xms1g -Xmx8g -jar vf-api.jar --spring.config.location=file:/config/application.yaml"]
