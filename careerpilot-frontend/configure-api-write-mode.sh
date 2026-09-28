#!/bin/sh
set -eu

config_file=/etc/nginx/conf.d/default.conf

case "${CAREERPILOT_APPLICATION_AUTH_ENABLED:-false}" in
    true)
        sed -i \
            '/^[[:space:]]*# CAREERPILOT_WRITE_GUARD_BEGIN$/,/^[[:space:]]*# CAREERPILOT_WRITE_GUARD_END$/d' \
            "$config_file"
        ;;
    false|'')
        ;;
    *)
        echo >&2 "CAREERPILOT_APPLICATION_AUTH_ENABLED must be true or false."
        exit 1
        ;;
esac
