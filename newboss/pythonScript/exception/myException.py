# -*- coding: utf-8 -*-
'''
Created on 2017年8月19日

@author: Simba
'''


class MyException(Exception):
    def __init__(self, message):
        Exception.__init__(self)
        self.message = message
        
    def __str__(self):
        return repr(self.message)
    