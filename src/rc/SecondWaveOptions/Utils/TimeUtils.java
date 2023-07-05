package rc.SecondWaveOptions.Utils;

import com.fs.starfarer.api.Global;

import java.io.IOException;

public class TimeUtils {

    public static void saveTime() throws IOException {
        String fileName = "timer";
        String newLine = System.lineSeparator();

        long lastTime = 0;
        String data = "";
        if (Global.getSettings().fileExistsInCommon(fileName)) {
            data = Global.getSettings().readTextFileFromCommon(fileName);
            String[] dataSegments = data.split("\\n");
            String lastLine = dataSegments[dataSegments.length - 1];
            // String[] lastLineSegments = lastLine.split(" ");
            // lastLineSegments[lastLineSegments.length - 1]
            lastTime = Long.parseLong(lastLine);
        }
        String date = String.format("%sDate: %s", newLine, Global.getSector().getClock().getDateString());
        data += date;
        long currentTime = System.currentTimeMillis();
        if (lastTime > 0) {
            long timeDifference = currentTime - lastTime;
            long timeDiffSeconds = timeDifference / 1000;
            long timeDiffMillis = timeDifference % 1000;
            String writeDiff = String.format("%sTime diff: %s.%s", newLine, timeDiffSeconds, timeDiffMillis);
            data += writeDiff;
        }
        String writeCurrent = String.format("%s%s", newLine, currentTime);
        data += writeCurrent;
        Global.getSettings().writeTextFileToCommon(fileName, data);
    }
}