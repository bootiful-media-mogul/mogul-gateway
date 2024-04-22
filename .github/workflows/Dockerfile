FROM ubuntu:22.04

ARG APP_NAME
ENV APP_NAME $APP_NAME

RUN echo "Building ${APP_NAME}..."
RUN apt-get update
RUN apt-get install -y ca-certificates imagemagick ffmpeg
RUN rm -rf /var/lib/apt/lists/*

COPY target/${APP_NAME} /app
CMD ["/app"]