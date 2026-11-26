# docker build -t blendx3d-android .
# docker run --rm -v "$PWD/..":/project blendx3d-android   (run from this folder; /project = unzipped project root)
FROM eclipse-temurin:17-jdk-jammy
ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools"
RUN apt-get update && apt-get install -y curl unzip git ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*
RUN mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp && \
    curl -fLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q clt.zip && mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm clt.zip && \
    yes | sdkmanager --licenses >/dev/null && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
WORKDIR /project
COPY build-apk.sh /usr/local/bin/build-apk.sh
CMD ["bash", "/usr/local/bin/build-apk.sh"]
