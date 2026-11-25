package com.halftonestudio;
import android.graphics.*;
import java.nio.file.Files;
import java.nio.file.Path;
public final class PerformanceInfrastructureV2525Test {
    private static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    public static void main(String[] ignored) throws Exception {
        CheckerboardPainter painter=new CheckerboardPainter(0xffeeeef0,0xffced1d3);
        Canvas c=new Canvas();
        for(int i=0;i<200;i++)painter.draw(c,150,200,5);
        check(c.draws==200,"Checkerboard should require one draw per frame");
        ProjectPhotoStore store=new ProjectPhotoStore();
        Path dir=Files.createTempDirectory("photo-store-test");
        Bitmap original=new Bitmap(12),photo=new Bitmap(34),crop=new Bitmap(56);
        store.save(dir.toFile(),photo,original,1);
        check(original.encoded==1&&photo.encoded==1,"first save");
        store.save(dir.toFile(),photo,original,1);
        check(original.encoded==1&&photo.encoded==1,"unchanged photo re-encoded");
        store.save(dir.toFile(),crop,original,2);
        check(original.encoded==1&&crop.encoded==1,"crop re-encoded original");
        check(Files.readAllBytes(PhotoRevisionStore.photo(dir.toFile()).toPath())[0]==56,"crop mismatch");
        check(!Files.exists(dir.resolve("editing-photo.png.pending")),"unfinished atomic save");
        store.reset();store.save(dir.toFile(),photo,original,3);
        check(original.encoded==2,"restore should persist original");
        ProjectPhotoStore.clear(dir.toFile());
        Files.delete(dir);
        CancellationCheckpoint.check();
        Thread.currentThread().interrupt();
        boolean canceled=false;
        try{CancellationCheckpoint.check();}catch(java.util.concurrent.CancellationException e){canceled=true;}
        Thread.interrupted();check(canceled,"interrupt must cancel");
        System.out.println("PASS reusable GPU tile draw count, atomic/lossless/dirty photo saves, cancellation");
    }
}
