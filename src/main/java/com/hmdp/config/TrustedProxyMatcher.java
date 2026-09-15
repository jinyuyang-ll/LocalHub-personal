package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accept forwarded client IP headers only when the direct peer is configured. */
@Slf4j
@Component
public class TrustedProxyMatcher {
    private final List<Network> trustedNetworks;

    public TrustedProxyMatcher(@Value("${localhub.http.trusted-proxies:}") String rules) {
        List<Network> parsed = new ArrayList<>();
        if (rules != null) {
            for (String rule : rules.split(",")) {
                String value = rule.trim();
                if (value.isEmpty()) continue;
                try {
                    parsed.addAll(Network.parse(value));
                } catch (UnknownHostException | IllegalArgumentException invalid) {
                    log.warn("Ignoring invalid trusted proxy rule: {}", value);
                }
            }
        }
        trustedNetworks = Collections.unmodifiableList(parsed);
    }

    public boolean matches(String remoteAddress) {
        if (remoteAddress == null || trustedNetworks.isEmpty()) return false;
        try {
            byte[] address = InetAddress.getByName(remoteAddress).getAddress();
            for (Network network : trustedNetworks) {
                if (network.contains(address)) return true;
            }
        } catch (UnknownHostException ignored) {
            return false;
        }
        return false;
    }

    private static final class Network {
        private final byte[] address;
        private final int prefixBits;

        private Network(byte[] address, int prefixBits) {
            this.address = address;
            this.prefixBits = prefixBits;
        }

        static List<Network> parse(String rule) throws UnknownHostException {
            String[] parts = rule.split("/", 2);
            InetAddress[] addresses = InetAddress.getAllByName(parts[0]);
            List<Network> networks = new ArrayList<>(addresses.length);
            for (InetAddress address : addresses) {
                int bits = parts.length == 1 ? address.getAddress().length * 8 : Integer.parseInt(parts[1]);
                if (bits < 0 || bits > address.getAddress().length * 8) {
                    throw new IllegalArgumentException("CIDR prefix out of range");
                }
                networks.add(new Network(address.getAddress(), bits));
            }
            return networks;
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != address.length) return false;
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != address[i]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (address[fullBytes] & mask);
        }
    }
}
