# Security Bundle: Nginx+ModSecurity3 + CrowdSec + Fail2Ban + Wazuh (Manager+Indexer+Dashboard)
# Debian 12 base, all-in-one container managed by supervisord
# Target: ~1.3 GB RAM with tuned heap sizes

FROM debian:12-slim AS modsec-build

# ── Build ModSecurity 3 from source ──────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential git ca-certificates automake autoconf libtool \
    libpcre3-dev libxml2-dev libcurl4-openssl-dev libyajl-dev \
    libgeoip-dev liblmdb-dev libfuzzy-dev pkg-config zlib1g-dev \
    libssl-dev && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build
RUN git clone --depth 1 -b v3/master https://github.com/owasp-modsecurity/ModSecurity.git && \
    cd ModSecurity && \
    git submodule init && git submodule update && \
    ./build.sh && \
    ./configure --with-pcre --with-lmdb && \
    make -j$(nproc) && \
    make install

# ── Build ModSecurity-nginx connector ────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpcre3-dev zlib1g-dev libssl-dev wget && \
    rm -rf /var/lib/apt/lists/*

# Get nginx version matching what we'll install in runtime
RUN NGINX_VER=$(apt-cache policy nginx 2>/dev/null | head -1 || echo "1.22.1") && \
    wget -q "http://nginx.org/download/nginx-1.22.1.tar.gz" && \
    tar xzf nginx-1.22.1.tar.gz

RUN git clone --depth 1 https://github.com/owasp-modsecurity/ModSecurity-nginx.git && \
    cd nginx-1.22.1 && \
    ./configure --with-compat --add-dynamic-module=../ModSecurity-nginx && \
    make modules && \
    mkdir -p /build/modules && \
    cp objs/ngx_http_modsecurity_module.so /build/modules/

# ── Download OWASP CRS ──────────────────────────────────────────────────────
RUN git clone --depth 1 -b v4.0/main https://github.com/coreruleset/coreruleset.git /build/crs && \
    cp /build/crs/crs-setup.conf.example /build/crs/crs-setup.conf

# ── Stage 2: Build ha-controller ─────────────────────────────────────────────
FROM golang:1.21-bookworm AS go-build
WORKDIR /build
COPY ha-controller/go.mod ha-controller/go.sum ./
RUN go mod download
COPY ha-controller/ .
RUN make build

# ══════════════════════════════════════════════════════════════════════════════
FROM debian:12-slim

LABEL maintainer="telcobright" \
      description="Night-watcher: Security bundle + HA controller"

ENV DEBIAN_FRONTEND=noninteractive \
    TERM=xterm-256color

# ── System packages ──────────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Nginx and deps (lua module needed for CrowdSec nginx bouncer)
    nginx libnginx-mod-http-lua lua-resty-core lua-resty-lrucache \
    curl wget gnupg2 apt-transport-https lsb-release ca-certificates \
    libpcre3 libxml2 libcurl4 libyajl2 libgeoip1 liblmdb0 libfuzzy2 \
    # Fail2Ban
    fail2ban iptables \
    # Python for log-shipper
    python3 python3-pip python3-dev default-libmysqlclient-dev \
    # Supervisord
    supervisor \
    # Utilities
    procps iproute2 jq cron logrotate vim-tiny netcat-openbsd dnsutils \
    default-mysql-client \
    && rm -rf /var/lib/apt/lists/*

# ── Python mysql connector ───────────────────────────────────────────────────
RUN pip3 install --no-cache-dir --break-system-packages mysql-connector-python

# ── Copy ModSecurity from builder ────────────────────────────────────────────
COPY --from=modsec-build /usr/local/modsecurity/ /usr/local/modsecurity/
COPY --from=modsec-build /build/modules/ngx_http_modsecurity_module.so /usr/lib/nginx/modules/
COPY --from=modsec-build /build/crs/ /etc/nginx/modsecurity/crs/

# Ensure modsecurity libs are found
RUN echo "/usr/local/modsecurity/lib" > /etc/ld.so.conf.d/modsecurity.conf && ldconfig

# Load modsecurity module in nginx
RUN mkdir -p /etc/nginx/modules-enabled && \
    echo 'load_module /usr/lib/nginx/modules/ngx_http_modsecurity_module.so;' > /etc/nginx/modules-enabled/50-modsecurity.conf

# ── Fake systemctl for package postinst scripts during build ─────────────────
# Many packages (CrowdSec, Wazuh) try to start/enable services during install.
# In Docker build there's no init system, so we provide a no-op shim.
RUN printf '#!/bin/sh\nexit 0\n' > /usr/bin/systemctl && chmod +x /usr/bin/systemctl

# ── Install CrowdSec ────────────────────────────────────────────────────────
RUN curl -fsSL https://packagecloud.io/install/repositories/crowdsec/crowdsec/script.deb.sh | bash && \
    apt-get install -y --no-install-recommends crowdsec crowdsec-firewall-bouncer-iptables && \
    rm -rf /var/lib/apt/lists/*

# Install CrowdSec nginx bouncer (HTTP API bouncer)
RUN apt-get update && \
    apt-get install -y --no-install-recommends crowdsec-nginx-bouncer || true && \
    rm -rf /var/lib/apt/lists/*

# ── Install Wazuh Manager ───────────────────────────────────────────────────
RUN curl -fsSL https://packages.wazuh.com/key/GPG-KEY-WAZUH | gpg --dearmor -o /usr/share/keyrings/wazuh.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/wazuh.gpg] https://packages.wazuh.com/4.x/apt/ stable main" > /etc/apt/sources.list.d/wazuh.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends wazuh-manager && \
    rm -rf /var/lib/apt/lists/*

# ── Install Wazuh Indexer (OpenSearch) ───────────────────────────────────────
# Pre-create dirs that the preinst script expects
RUN mkdir -p /etc/wazuh-indexer /var/lib/wazuh-indexer /var/log/wazuh-indexer && \
    apt-get update && \
    apt-get install -y --no-install-recommends wazuh-indexer && \
    rm -rf /var/lib/apt/lists/*

# ── Install Wazuh Dashboard ─────────────────────────────────────────────────
RUN mkdir -p /etc/wazuh-dashboard && \
    apt-get update && \
    apt-get install -y --no-install-recommends wazuh-dashboard && \
    rm -rf /var/lib/apt/lists/*

# ── Remove fake systemctl ───────────────────────────────────────────────────
RUN rm -f /usr/bin/systemctl

# ── Directory structure ──────────────────────────────────────────────────────
RUN mkdir -p \
    /etc/nginx/snippets \
    /etc/nginx/modsecurity \
    /etc/nginx/sites-enabled \
    /var/log/nginx \
    /var/log/security-bundle \
    /var/log/modsec_audit \
    /config \
    /opt/security-bundle/scripts

# ── Copy common configs (overwritten by entrypoint with tenant-specific) ─────
COPY common/nginx/snippets/ /etc/nginx/snippets/
COPY common/modsecurity/ /etc/nginx/modsecurity/
COPY common/wazuh/ /opt/security-bundle/wazuh-common/
COPY common/scripts/ /opt/security-bundle/scripts/
COPY common/mysql/ /opt/security-bundle/mysql/

# ── Copy entrypoint and supervisord ──────────────────────────────────────────
COPY entrypoint.sh /entrypoint.sh
COPY supervisord.conf /etc/supervisor/conf.d/security-bundle.conf

# ── Copy hactl binary from Go build stage ─────────────────────────────────────
COPY --from=go-build /build/bin/hactl /usr/local/bin/hactl

RUN chmod +x /entrypoint.sh /opt/security-bundle/scripts/*.sh /opt/security-bundle/scripts/*.py || true

# ── Tune OpenSearch for low memory ───────────────────────────────────────────
ENV OPENSEARCH_JAVA_OPTS="-Xms384m -Xmx384m"

# ── Ports ────────────────────────────────────────────────────────────────────
# 80/443: HTTP/HTTPS (nginx)
# 1514: Wazuh agent registration
# 1515: Wazuh agent auth
# 5601: Wazuh Dashboard
# 9200: Wazuh Indexer (OpenSearch)
# 55000: Wazuh API
# 7102: hactl status API
EXPOSE 80 443 1514 1515 5601 9200 55000 7102

ENTRYPOINT ["/entrypoint.sh"]
