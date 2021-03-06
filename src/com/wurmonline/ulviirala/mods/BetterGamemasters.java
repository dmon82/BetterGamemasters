package com.wurmonline.ulviirala.mods;

import java.lang.reflect.Modifier;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Descriptor;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

/**
 * Implements several things that are meant to make a Gamemaster's life a bit
 * easier, by providing utility and convenience.
 */
public class BetterGamemasters implements WurmServerMod, Configurable, PreInitable, ServerStartedListener {
    private static final Logger _logger = Logger.getLogger(BetterGamemasters.class.getName() + " v1.2");

    // Whether or not this option should be enabled or not.
    private boolean _noCarryWeightLimit = true;
    
    // The MINIMUM player MGMT power for this to be active.
    private int _noCarryWeightLimitPower = 1;
    
    private boolean _notSlowedByWeight = true;
    private int _notSlowedByWeightPower = 1;
    
    private boolean _noDamageOnGamemasterOwnedItems = true;
    private int _noDamageOnGamemasterOwnedItemsPower = 1;

    private boolean _noFloorBuildingRequirements = true;
    
    private boolean _noItemLimit = true;
    private int _noItemLimitPower = 1;

    private boolean _noDropItemLimit = true;
    
    @Override
    public void configure(Properties properties) {
        _noCarryWeightLimit = Boolean.valueOf(properties.getProperty("noCarryWeightLimit", Boolean.toString(_noCarryWeightLimit)));
        _noCarryWeightLimitPower = Integer.valueOf(properties.getProperty("noCarryWeightLimitPower", Integer.toString(_noCarryWeightLimitPower)));
        
        _notSlowedByWeight = Boolean.valueOf(properties.getProperty("notSlowedByWeight", Boolean.toString(_notSlowedByWeight)));
        _notSlowedByWeightPower = Integer.valueOf(properties.getProperty("notSlowedByWeightPower", Integer.toString(_notSlowedByWeightPower)));
        
        _noDamageOnGamemasterOwnedItems = Boolean.valueOf(properties.getProperty("noDamageOnGamemasterOwnedItems", Boolean.toString(_noDamageOnGamemasterOwnedItems)));
        _noDamageOnGamemasterOwnedItemsPower = Integer.valueOf(properties.getProperty("noDamageOnGamemasterOwnedItemsPower", Integer.toString(_noDamageOnGamemasterOwnedItemsPower)));

        _noFloorBuildingRequirements = Boolean.valueOf(properties.getProperty("noFloorBuildingRequirements", Boolean.toString(_noFloorBuildingRequirements)));
        
        _noItemLimit = Boolean.valueOf(properties.getProperty("noItemLimit", Boolean.toString(_noItemLimit)));
        _noItemLimitPower = Integer.valueOf(properties.getProperty("noItemLimitPower", String.valueOf(_noItemLimitPower)));
        
        _noDropItemLimit = Boolean.valueOf(properties.getProperty("noDropItemLimit", String.valueOf(_noDropItemLimit)));
        
        Log("No carry weight limit: ", _noCarryWeightLimit, _noCarryWeightLimitPower);
        Log("Not slowed by inventory weight: ", _notSlowedByWeight, _notSlowedByWeightPower);
        Log("No damage on GM owned items: ", _noDamageOnGamemasterOwnedItems, _noDamageOnGamemasterOwnedItemsPower);
        Log("No Floor building requirements: ", _noFloorBuildingRequirements, 2);
        Log("No item limit: ", _noItemLimit, _noItemLimitPower);
    }
    
    @Override
    public void onServerStarted() {
        ModActions.registerAction(new SpawnTowerGuardAction());
    }
    
    private void Log(String forFeature, boolean activated, int power) {
        /* 
        * Logs as "Feature name: true for MGMT powers n and above",
        * or "Feature name: false".
        */
        _logger.log(Level.INFO, forFeature + activated + 
                (activated 
                        ? (" for MGMT powers " + power + " and above.")
                        : (".")));
    }

    @Override
    public void preInit() {
        SpawnTowerGuards();
        ModActions.init();
        
        if (_noCarryWeightLimit) NoCarryWeightLimit();
        if (_notSlowedByWeight) NotSlowedByWeight();
        if (_noDamageOnGamemasterOwnedItems) NoDamageOnGamemasterOwnedItems();
        if (_noFloorBuildingRequirements) NoFloorBuildingRequirements();
        if (_noDropItemLimit) NoDropItemLimit();
    }
    
    private void SpawnTowerGuards() {
        _logger.info("Trying to mal pollGuards() methods public.");
        
        try {
            CtMethod method = HookManager.getInstance().getClassPool().get("com.wurmonline.server.kingdom.GuardTower")
                    .getMethod("pollGuards", "()V");
            method.setModifiers((method.getModifiers() & ~Modifier.PRIVATE) | Modifier.PUBLIC);
            
            method = HookManager.getInstance().getClassPool().get("com.wurmonline.server.villages.GuardPlan")
                    .getMethod("pollGuards", "()V");
            method.setModifiers((method.getModifiers() & ~Modifier.PRIVATE) | Modifier.PUBLIC);
            
            _logger.info("pollGuards() methods are now public.");
        }
        catch (Exception e) {
            _logger.log(Level.SEVERE, "Couldn't make pollGuards() methods public.", e);
        }
    }

    private void NoDropItemLimit() {
        _logger.info("Applying patch for no drop item limit.");
        
        try {
            CodeIterator it =
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.MethodsItems")
                    .getMethod("drop", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Z)[Ljava/lang/String;")
                    .getMethodInfo()
                    .getCodeAttribute()
                    .iterator();
            
            while (it.hasNext()) {
                int index = it.next();
                int opcode = it.byteAt(index);
                
                if (opcode == Opcode.BIPUSH && it.byteAt(index + 1) == 120) {
                    _logger.log(Level.INFO, "Found opcode at index #{0}.", index);
                    it.write(new byte[] { 0x00, 0x00 }, index);
                    it.insertAt(index, new byte[] { 0x11, 32767 >> 8, (byte)(32767 & 0xFF) } );
                    break;
                }
            }
            
            _logger.info("Removed drop limit for non-players.");
        }
        catch (NotFoundException | BadBytecode e) {
            _logger.log(Level.SEVERE, "Can't apply patch for no drop item limit.", e);
        }           
    }
    
    /**
     * Effectively makes players with the set MGMT power or above able to pick
     * up items beyond their body strength limit.
     * 
     * Technically, this is done by overriding the canCarry(I)Z method,
     * returning true whenever their MGMT power is high enough.
     */
    private void NoCarryWeightLimit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature");
            CtClass[] parameters = new CtClass[]{CtPrimitiveType.intType};
            CtMethod method = ctClass.getMethod("canCarry", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            method.insertBefore("{ if (this.getPower() >= " + _noCarryWeightLimitPower + ") return true; }");
            
            method = ctClass.getMethod("getCarryCapacityFor", Descriptor.ofMethod(CtPrimitiveType.intType, parameters));
            method.insertBefore("{ if (this.getPower() >= " + _noCarryWeightLimitPower + ") return 0x7FFFFFFF; }");
            
            method = ctClass.getMethod("getCarryingCapacityLeft", Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[0]));
            method.insertBefore("{ if (this.getPower() >= " + _noCarryWeightLimitPower + ") return 0x7FFFFFFF; }");
            
        } catch (Exception ex) {
            throw new HookException(ex);
        }
    }

    /**
     * Makes characters with a set MGMT power or above to be not slowed down
     * in movement speed by the weight they're carrying.
     * 
     * Technically this is done by setting the thresholds for "move slow",
     * "encumbered" and "cantmove" to 2^31-1, or 0x7FFFFFFF, or to be precise
     * to exactly "about 2 billion grams", the maximum positive signed 32 bit
     * integer value.
     */
    private void NotSlowedByWeight() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature");
            CtClass[] parameters = new CtClass[0];
            CtMethod method = ctClass.getMethod("setMoveLimits", Descriptor.ofMethod(CtPrimitiveType.voidType, parameters));

            method.insertBefore("{ if (this.getPower() >= " + _notSlowedByWeightPower + ") { this.moveslow = this.encumbered = this.cantmove = 0x7FFFFFFF; return; } }");
            
            method = null;
            parameters = null;
            ctClass = null;
        } catch (CannotCompileException | NotFoundException ex) {
            throw new HookException(ex);
        }
    }
    
    /**
     * Effectively makes any item that is owned by a players with the set
     * MGMT power take no damage at all. That includes tools through use,
     * weapons, even fences or house wall. Everything that is a DbItem taking
     * damage through the DbItem.setDamage(FZ)Z method.
     * 
     * Technically this is done by inserting a code snippet that can be found
     * in com.wurmonline.server.items.Item.getOwnerOrNull()LCreature; returning
     * the owner or null, then polling the getPower()I MGMT power value.
     */
    private void NoDamageOnGamemasterOwnedItems() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.DbItem");
            CtClass[] parameters = {
                CtPrimitiveType.floatType,
                CtPrimitiveType.booleanType
            };
            CtMethod method = ctClass.getMethod("setDamage",
                    Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            
            /*
            * Inserts the following pseudo-code at the head of the method:
            
                if (item owner's MGMT power >= minimum power for feature)
                    return false; // apply no damage, false means item not destroyed.
            
            * Actual code is:
                try {
                    long ownerID = this.getOwnerId();
            
                    if (ownerID != -10L && Server.getInstance().getCreature(ownerID).getPower() >= minPower)
                        return false;
                } catch (NoSuchCreatureException nsc) {
                    DbItem.logger.log(Level.WARNING, nsc.getMessage(), (Throwable)nsc);
                } catch (NoSuchPlayerException nsp) {
                    if (PlayerInfoFactory.getPlayerInfoWithWurmId(this.getOwnerId()) == null)
                        DbItem.logger.log(Level.WARNING, nsp.getMessage(), (Throwable)nsp);
                }
            */
            
            // Needs fully qualified names or fails compiling.
            method.insertBefore(
                    "{"
                        + "try {" 
                        + "long bgOwnerID = this.getOwnerId();"
                        + "if (bgOwnerID != -10L && com.wurmonline.server.Server.getInstance().getCreature(bgOwnerID).getPower() >= " + _noDamageOnGamemasterOwnedItems + ") return false;"
                        + "} catch (com.wurmonline.server.creatures.NoSuchCreatureException nsc) {"
                        + "com.wurmonline.server.items.DbItem.logger.log(java.util.logging.Level.WARNING, nsc.getMessage(), (Throwable)nsc); "
                        + "} catch (com.wurmonline.server.NoSuchPlayerException nsp) {"
                        + "if (com.wurmonline.server.players.PlayerInfoFactory.getPlayerInfoWithWurmId(this.getOwnerId()) == null)"
                        + "com.wurmonline.server.items.DbItem.logger.log(java.util.logging.Level.WARNING, nsp.getMessage(), (Throwable)nsp); }"
                    + "}");
            
            method = null;
            parameters = null;
            ctClass = null;
            
        } catch (NotFoundException | CannotCompileException ex) {
            throw new HookException(ex);
        }
    }
    
    /**
     * Effectively makes Gamemasters of MGMT powers 2 and higher require no
     * skill or material building floors below, above, floors with opening,
     * staircases on floors below, and roofs.
     * 
     * Technically this is done by having the "boolean insta" variable in the
     * floorBuilding(LAction;LCreature;LItem;LFloor;SF)Z method set to true.
     * Normally this applies only on test servers, we replace the call to
     * Servers.isThisATestServer() with true.
     */
    private void NoFloorBuildingRequirements() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.FloorBehaviour");
            CtClass[] parameters = {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Action"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.structures.Floor"),
                CtPrimitiveType.shortType,
                CtPrimitiveType.floatType
            };
            CtMethod method = ctClass.getMethod("floorBuilding",
                    Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            
            // Changes the "Servers.isThisATestServer()" call to "true".
            method.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    String methodName = methodCall.getMethodName();
                    
                    if (methodName.equals("isThisATestServer"))
                        methodCall.replace("$_ = true;");
                }
            });
            
            method = null;
            parameters = null;
            ctClass = null;
        } catch (NotFoundException | CannotCompileException ex) {
            throw new HookException(ex);
        }
    }
}
