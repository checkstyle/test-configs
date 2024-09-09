package com.github.checkstyle.difftool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiffToolTest {

    @BeforeEach
    void setUp() {

    }

    @Test
    void testMain() {
        String srcDir = "/";
        String excludes = "exclude1,exclude2";
        String checkstyleConfig = DiffToolTest.class.getResource("/checkstyle.xml").getPath();
        String checkstyleVersion = "8.41";
        String extraRegressionOptions = "-DskipTests=true";

        DiffTool.main(new String[0]);

    }
}