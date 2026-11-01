import net.kdt.pojavlaunch.aerix.client.AdaptiveDistanceController;
public final class AdaptiveDistanceSmokeTest {
    private static void eq(int a, int b) { if (a != b) throw new AssertionError(a + " != " + b); }
    public static void main(String[] args) {
        AdaptiveDistanceController c = new AdaptiveDistanceController(8, 4, 60);
        eq(8, c.sample(30)); eq(7, c.sample(30));
        eq(7, c.sample(70)); eq(7, c.sample(70)); eq(8, c.sample(70));
        for (int i = 0; i < 20; i++) c.sample(20);
        eq(4, c.current());
        for (int i = 0; i < 20; i++) c.sample(80);
        eq(8, c.current());
        c.configure(6, 30); eq(8, c.sample(0));
        System.out.println("Adaptive distance smoke tests passed");
    }
}
