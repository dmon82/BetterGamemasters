/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wurmonline.ulviirala.mods;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;

/**
 * Removes carry limits for non-players.
 * 
 * Make flattening actions for non-players a quickLevel, ignoring weight
 * and item count limits. Also makes all canCarry (I)Z calls return true
 * for non-players.
 */
public class BetterGamemasters implements WurmMod, PreInitable {
    @Override
    public void preInit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature");
            CtClass[] parameters = new CtClass[] { CtPrimitiveType.intType };
            CtMethod method = ctClass.getMethod("canCarry", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            method.insertBefore("{ if (this.getPower() > 0) return true; }");
            
            parameters = new CtClass[0];
            method = ctClass.getMethod("setMoveLimits", Descriptor.ofMethod(CtPrimitiveType.voidType, parameters));
            method.insertBefore("{ if (this.getPower() > 0) { this.moveslow = this.encumbered = this.cantmove = 0x7FFFFFFF; return; } }");
            
            method = null;
            parameters = null;
            ctClass = null;
        }
        catch (NotFoundException | CannotCompileException e) {
            throw new HookException(e);
        }
    }
}
