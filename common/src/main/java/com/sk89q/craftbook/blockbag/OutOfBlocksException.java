// $Id$
/*
 * CraftBook
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.craftbook.blockbag;


/**
 *
 * @author sk89q
 */
public class OutOfBlocksException extends BlockBagException {
    private static final long serialVersionUID = -7063726368378723452L;
    /**
     * Stores the block ID.
     */
    private int id;

    /**
     * Construct the object.
     * @param id
     */
    public OutOfBlocksException(int id) {
        this.id = id;
    }

    /**
     * @return the id
     */
    public int getID() {
        return id;
    }
}
