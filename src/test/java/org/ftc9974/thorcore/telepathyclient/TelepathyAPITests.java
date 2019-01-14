package org.ftc9974.thorcore.telepathyclient;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TelepathyAPITests {

    @Test
    public void test_validation() {
        List<Byte> packet = List.of(
                (byte) 0,
                (byte) 70,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 1,
                (byte) 75
        );

        Assert.assertTrue(TelepathyAPI.validatePacket(packet));
    }
}
