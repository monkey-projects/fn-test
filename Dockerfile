# This Dockerfile is required to override the default build behaviour
FROM clojure:temurin-17-tools-deps-alpine AS build-stage

WORKDIR /build

# Build an uberjar
ADD . /build
RUN ["clojure", "-T:build", "uber"]

# 2nd stage, set up the run env using fnproject base image
FROM fnproject/fn-java-fdk:jre17-latest

WORKDIR /function

COPY --from=build-stage /build/target/*-standalone.jar /function/app/
CMD ["monkey.fn_test.core::handler"]
