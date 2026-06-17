# TeamCity

Use the platform pipeline generator in `k8s-platform/scripts/teamcity/create_pipeline.ps1` with a config entry for `notification-service`.

Required parameters:

- `docker.username`
- `docker.password`
- `github.username`
- `github.token`

The build must use Docker Buildx with `linux/amd64,linux/arm64`.
