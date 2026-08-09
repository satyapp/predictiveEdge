package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class JdkZerodhaTransportTest {
    @Test
    void decodesPlainAndGzipBodiesAsUtf8() throws Exception {
        String body = "instrument_token,tradingsymbol\n408065,INFY";

        assertThat(JdkZerodhaTransport.decode(body.getBytes(StandardCharsets.UTF_8), ""))
                .isEqualTo(body);
        assertThat(JdkZerodhaTransport.decode(gzip(body), "gzip"))
                .isEqualTo(body);
    }

    private static byte[] gzip(String value) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
}
