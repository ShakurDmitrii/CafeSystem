# MinIO больше не публикует community-образы в Docker Hub, поэтому для offline
# image-pack собираем тот же релиз из исходников. Тег образа совпадает с тегом в
# desktop/runtime/docker-compose.yml, а `mc` нужен healthcheck'у `mc ready local`.
FROM golang:1.24-bookworm AS build

ARG MINIO_TAG=RELEASE.2025-09-07T16-13-09Z
ARG MC_TAG=RELEASE.2025-08-13T08-35-41Z
ENV CGO_ENABLED=0 GOOS=linux GOARCH=amd64

RUN git clone --depth 1 --branch "$MINIO_TAG" https://github.com/minio/minio /src/minio \
    && cd /src/minio \
    && go build -trimpath -ldflags "$(go run buildscripts/gen-ldflags.go)" -o /out/minio .

RUN git clone --depth 1 --branch "$MC_TAG" https://github.com/minio/mc /src/mc \
    && cd /src/mc \
    && go build -trimpath -o /out/mc .

FROM debian:bookworm-slim
ARG MINIO_TAG=RELEASE.2025-09-07T16-13-09Z
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.version="$MINIO_TAG"
COPY --from=build /out/minio /out/mc /usr/bin/
EXPOSE 9000 9001
VOLUME ["/data"]
ENTRYPOINT ["/usr/bin/minio"]
CMD ["server", "/data"]
