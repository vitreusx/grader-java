package othersol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/*KOSTKA - KLASA ROBIĄCA OBROTY NA KOSTCE
* Sara Lukasik 27.11.2021.*/
public class Cube implements solution.ICube {
    private  BiConsumer<Integer, Integer> beforeRotation;
    private  Runnable beforeShowing;
    private  BiConsumer<Integer, Integer> afterRotation;
    private  Runnable afterShowing;
    private  int size;
    private  int[][][] cube;
    private  final Semaphore semaphoregate = new Semaphore(1, true);
    private final Semaphore listsafer = new Semaphore(1, true);
    private  final Semaphore waiter = new Semaphore(0, true);

    private  Semaphore[][] semaphorelayer;
    private  int currentside = 0;
    private  ArrayList<Thread> workingoncube;
    private int waitng = 0;
    private AtomicInteger countme = new AtomicInteger(0);

    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        workingoncube= new ArrayList<Thread>();

        cube = new int[6][size][size];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < size; j++) {
                cube[i][j] = new int[size];
                Arrays.fill(cube[i][j], i);
            }
        }
        this.semaphorelayer = new Semaphore[3][size];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < size; j++) {
                semaphorelayer[i][j] = new Semaphore(1, false);
            }
        }
    }

    private static int parallel(int side){
        int toreturn = -1;
        switch (side){
            case 0:
                toreturn = 5;
                break;
            case 1:
                toreturn = 3;
                break;
            case 2:
                toreturn = 4;
                break;
            case 3:
                toreturn = 1;
                break;
            case 4:
                toreturn = 2;
                break;
            case 5:
                toreturn = 0;
                break;
        }
        return toreturn;
    }

    public void rotate(int side, int layer) throws InterruptedException {

        try {

            semaphoregate.acquire();

        } catch (InterruptedException e) {

            throw e;

        }
        waitng = 0;

        if (!(side == currentside || side == parallel(side) || countme.get() == 0)){
            try{
            listsafer.acquire();}
            catch (InterruptedException e){
                semaphoregate.release();
                throw e;
            }

            if (countme.get() != 0){
                waitng = 1;
            }

            listsafer.release();

            if (waitng ==1){

                waiter.acquireUninterruptibly();

            }
            countme.set(0);

        }


        waitng = 0;
        currentside = side;


        countme.getAndIncrement();
        semaphoregate.release();



        int i = -1;
        int j = -1;
        switch (side) {
            case 0 :
                i = 0;
                j = layer;
                break;

            case 1:
                i = 1;
                j = layer;
                break;

            case 2:
                i = 2;
                j = layer;
                break;

            case 3 :
                i = 1;
                j = size - layer - 1;
                break;

            case 4:
                i = 2;
                j = size - layer - 1;
                break;

            case 5:
                i = 0;
                j = size - layer - 1;
                break;
        }


        try {
            semaphorelayer[i][j].acquire();

        } catch (InterruptedException e) {

            listsafer.acquireUninterruptibly();
            if (countme.decrementAndGet() == 0 && waitng == 1){
                waiter.release();
            }
            listsafer.release();
            throw e;
        }

        beforeRotation.accept(side, layer);
        rotatering(side,layer);
        afterRotation.accept(side, layer);


        listsafer.acquireUninterruptibly();

        if (countme.decrementAndGet() == 0 && waitng == 1){
            waiter.release();
        }
        listsafer.release();

        semaphorelayer[i][j].release();

    }

    public String showseq() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    s.append(String.format("%d", cube[i][j][k]));
                }
            }
        }
        return s.toString();
    }

    public String show() throws InterruptedException {

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < size; j++) {
                try{
                    semaphorelayer[i][j].acquire();}
                catch (InterruptedException e){
                    throw e;
                }
            }
        }
        beforeShowing.run();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    s.append(String.format("%d", cube[i][j][k]));

                }
            }
        }
        afterShowing.run();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < size; j++) {
                semaphorelayer[i][j].release();
            }
        }

        return s.toString();
    }

    public void rotateside(int side, boolean turnright){
        int[][] tempcube = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(cube[side][i], 0, tempcube[i], 0, size);
        }
        if (turnright){
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    cube[side][i][j] = tempcube[size-1-j][i];
                }

            }
        }
        else{
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    cube[side][i][j] = tempcube[j][size-1-i];
                }
            }
        }
    }

    public void rotatering(int side, int which){

        switch(side) {
            case 0:
                for (int i = 0; i < size; i++) {
                    int temp = cube[2][which][i];
                    cube[2][which][i] =  cube[3][which][i];
                    cube[3][which][i] = cube[4][which][i];
                    cube[4][which][i] = cube[1][which][i];
                    cube[1][which][i] = temp;
                }
                break;
            case 1:
                for (int i = 0; i < size; i++) {
                    int temp = cube[5][i][which];
                    cube[5][i][which] = cube[2][i][which];
                    cube[2][i][which] = cube[0][i][which];
                    cube[0][i][which] = cube[4][size-1-i][size-1-which];
                    cube[4][size-1-i][size-1-which] = temp;
                }
                break;
            case 2:
                for (int i = 0; i < size; i++) {
                    int temp = cube[0][size - 1- which][i];
                    cube[0][size - 1- which][i] = cube[1][size-1-i][size-1-which];
                    cube[1][size-1-i][size-1-which] = cube[5][which][size-1-i];
                    cube[5][which][size-1-i]= cube[3][i][which];
                    cube[3][i][which] = temp;
                }

                break;
            case 3:
                for (int i = 0; i < size; i++) {
                    int temp = cube[5][i][size-1-which];
                    cube[5][i][size-1-which] = cube[4][size-1-i][which];
                    cube[4][size-1-i][which] = cube[0][i][size-1-which];
                    cube[0][i][size-1-which] = cube[2][i][size-1-which];
                    cube[2][i][size-1-which] = temp;
                }
                break;
            case 4:
                for (int i = 0; i < size; i++) {
                    int temp = cube[5][size-1-which][size-1-i];
                    cube[5][size-1-which][size-1-i] = cube[1][size-1-i][which];
                    cube[1][size-1-i][which] = cube[0][which][i];
                    cube[0][which][i] = cube[3][i][size-1-which];
                    cube[3][i][size-1-which] = temp;
                }
                break;
            case 5:
                for (int i = 0; i < size; i++) {
                    int temp = cube[2][size-1 -which][i];
                    cube[2][size-1 -which][i] =  cube[1][size-1 -which][i];
                    cube[1][size-1 -which][i] =  cube[4][size-1 -which][i];
                    cube[4][size-1 -which][i] =  cube[3][size-1 -which][i];
                    cube[3][size-1 -which][i] = temp;
                }
                break;
        }
        if (which == 0){
            rotateside(side, true);
        }
        else if (which == size-1){
            rotateside(parallel(side), false);

        }
    }
    public void setvalue(int side, int row, int column, int what){
        cube[side][row][column] = what;
    }
    public int getvalue(int side, int row, int column){
        return  cube[side][row][column];
    }
    public int getsize(){
        return size;
    }
}