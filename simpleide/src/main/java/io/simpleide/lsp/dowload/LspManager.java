package io.simpleide.lsp.dowload;

import java.io.IOException;
import java.io.InputStream;

public class LspManager {
    public static String getComandRun(String linguage){
        return "";
    }
    
    public static LspRegistry getRegistry(){
        try(InputStream inputStream = LspManager.class.getResourceAsStream("registry.json")){
            
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
