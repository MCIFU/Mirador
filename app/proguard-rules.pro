# Las rutas de Navigation Compose se serializan con kotlinx.serialization;
# el plugin genera los serializers y R8 los conserva mediante las reglas del propio runtime.

# Sin ofuscar: los nombres originales permiten que el perfil de referencia (baseline-prof.txt),
# escrito con comodines sobre los paquetes, siga encajando tras R8. La optimización y la
# reducción de código se mantienen.
-dontobfuscate
