package com.github.jeanbaptistewatenberg.junit;

public class TestContainersTestDescription implements TestDescription {
    private final String testId;
    private final String filesystemFriendlyNameOf;

    public TestContainersTestDescription(String testId, String filesystemFriendlyNameOf) {
        this.testId = testId;
        this.filesystemFriendlyNameOf = filesystemFriendlyNameOf;
    }

    @Override
    public String getTestId() {
        return testId;
    }

    @Override
    public String getFilesystemFriendlyName() {
        return filesystemFriendlyNameOf;
    }
}
