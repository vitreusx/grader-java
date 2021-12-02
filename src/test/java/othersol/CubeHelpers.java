package othersol;

import java.util.Random;

/*CUBEHELPERS - KLASA ZAIMPLENETOWANA DO ŁATWEJSZEGO TESTOWANIA
 * Sara Lukasik 27.11.2021.*/
public class CubeHelpers  {

    Random rand = new Random();

    public void resetCube(Cube c){
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < c.getsize(); j++) {
                for (int k = 0; k < c.getsize(); k++) {
                    c.setvalue(i,j,k,i);
                }
            }
        }
    }

    public int[][] generaterandomrotates(Cube c, int howmany, int range){
        int[][] intstructions;
        if (howmany == -1){
            intstructions = new int[rand.nextInt(range)+1][2];
        }
        else{
            intstructions = new int[howmany][2];
        }
        for (int i = 0; i < intstructions.length; i++) {
            intstructions[i][0] = rand.nextInt(6);
            intstructions[i][1] = rand.nextInt(c.getsize());
        }

        return intstructions;
    }
    
    public int checksides(Cube c){
        int size = c.getsize();
        int[] colors = {0,0,0,0,0,0,0};
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    colors[c.getvalue(i,j,k)]+=1;
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            if (colors[i] !=size*size){
                return -1;
            }
        }
        return 0;

    }


}
