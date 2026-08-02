package net.neganote.gtutilities.utils;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Compiled whitelist/blacklist tag expressions plus a decision cache, shared by the tag stocking parts.
 * <p>
 * {@link #update(String, String)} has to be called before {@link #isAllowed(AEKey)} so that edited expressions are
 * recompiled; both expressions live on the machine so that they can be saved and synced.
 */
public final class TagFilter {

    private final Object2ByteOpenHashMap<AEKey> decisionCache = new Object2ByteOpenHashMap<>();
    private final int decisionCacheLimit;

    private @Nullable String whitelist = null;
    private @Nullable String blacklist = null;
    private TagMatcher.Compiled whitelistCompiled = TagMatcher.compile("");
    private TagMatcher.Compiled blacklistCompiled = TagMatcher.compile("");

    @Getter
    private boolean whitelistBadSyntax = false;
    @Getter
    private boolean blacklistBadSyntax = false;

    public TagFilter(int decisionCacheLimit) {
        this.decisionCacheLimit = decisionCacheLimit;
        this.decisionCache.defaultReturnValue((byte) -1);
    }

    /**
     * Drops every cached decision and forces both expressions to be recompiled on the next update.
     */
    public void invalidate() {
        whitelist = null;
        blacklist = null;
        decisionCache.clear();
    }

    public void update(@Nullable String whitelistExpr, @Nullable String blacklistExpr) {
        String wl = norm(whitelistExpr);
        String bl = norm(blacklistExpr);

        if (!Objects.equals(wl, whitelist)) {
            whitelist = wl;
            whitelistCompiled = TagMatcher.compile(wl);
            whitelistBadSyntax = !whitelistCompiled.isValid();
            decisionCache.clear();
        }
        if (!Objects.equals(bl, blacklist)) {
            blacklist = bl;
            blacklistCompiled = TagMatcher.compile(bl);
            blacklistBadSyntax = !blacklistCompiled.isValid();
            decisionCache.clear();
        }

        if (decisionCache.size() > decisionCacheLimit) {
            decisionCache.clear();
        }
    }

    /**
     * @return whether the key passes the blacklist and is matched by the whitelist. Nothing is allowed while either
     *         expression fails to compile, or while both are empty.
     */
    public boolean isAllowed(AEKey key) {
        if (whitelistBadSyntax || blacklistBadSyntax) return false;
        if (isEmpty(whitelist) && isEmpty(blacklist)) return false;

        byte cached = decisionCache.getByte(key);
        if (cached != -1) {
            return cached == 1;
        }

        boolean allowed;
        if (!isEmpty(blacklist) && blacklistCompiled.isValid() && matches(key, blacklistCompiled)) {
            allowed = false;
        } else {
            allowed = !isEmpty(whitelist) && whitelistCompiled.isValid() && matches(key, whitelistCompiled);
        }

        decisionCache.put(key, allowed ? (byte) 1 : (byte) 0);
        return allowed;
    }

    private static boolean matches(AEKey key, TagMatcher.Compiled compiled) {
        if (key instanceof AEItemKey itemKey) return TagMatcher.doesItemMatch(itemKey, compiled);
        if (key instanceof AEFluidKey fluidKey) return TagMatcher.doesFluidMatch(fluidKey, compiled);
        return false;
    }

    private static boolean isEmpty(@Nullable String expr) {
        return expr == null || expr.isEmpty();
    }

    private static String norm(@Nullable String expr) {
        return expr == null ? "" : expr.trim();
    }
}
